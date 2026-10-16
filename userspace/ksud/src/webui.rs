//! Local web management. Authentication is required for every API request.
use crate::{defs, feature, feature_id::FeatureId, ksucalls, module};
use anyhow::{Context, Result, bail, ensure};
use serde_json::{Value, json};
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::net::TcpStream;
use std::os::unix::fs::{MetadataExt, OpenOptionsExt, PermissionsExt};
use std::os::unix::process::CommandExt;
use std::path::Path;
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};
use tiny_http::{Header, Method, Request, Response, Server};

const ADDRESS: &str = "127.0.0.1:9178";
const ORIGIN: &str = "http://127.0.0.1:9178";
const MAX_UPLOAD: u64 = 256 * 1024 * 1024;
const LAUNCHER: &str = "com.xinglinghu.manager.Launcher";

#[derive(clap::Subcommand, Debug)]
pub enum WebCommand {
    Start,
    Stop,
    /// Print the authenticated browser URL
    Url,
    /// Revoke the current browser link and mint a new one
    Rotate,
    /// Persist whether the service starts at boot
    Autostart {
        #[arg(value_parser = ["on", "off", "status"])]
        mode: String,
    },
    #[command(hide = true)]
    Serve,
    /// Internal root-only subprocess; the server keeps its original credentials.
    #[command(hide = true)]
    Grant {
        package: String,
        uid: i32,
        #[arg(long, action = clap::ArgAction::Set)]
        granted: bool,
    },
}

fn path(name: &str) -> std::path::PathBuf {
    Path::new(defs::WORKING_DIR).join(name)
}
pub fn autostart_enabled() -> bool {
    path("webui.autostart").exists()
}

fn prepare_directory() -> Result<()> {
    fs::create_dir_all(defs::WORKING_DIR)?;
    let metadata = fs::symlink_metadata(defs::WORKING_DIR)?;
    ensure!(
        metadata.is_dir() && metadata.uid() == 0 && metadata.mode() & 0o022 == 0,
        "Web manager directory must be owned by root and not writable by other users"
    );
    Ok(())
}

fn random_token() -> Result<String> {
    let mut random = [0u8; 32];
    File::open("/dev/urandom")?.read_exact(&mut random)?;
    Ok(base16ct::lower::encode_string(&random))
}

fn write_private(name: &str, value: &str) -> Result<()> {
    let temporary = path(&format!(
        "{name}.{}.{}",
        std::process::id(),
        random_token()?
    ));
    let result = (|| -> Result<()> {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o600)
            .custom_flags(libc::O_NOFOLLOW)
            .open(&temporary)?;
        file.set_permissions(fs::Permissions::from_mode(0o600))?;
        ensure!(
            file.metadata()?.uid() == 0,
            "Web manager credentials must be owned by root"
        );
        file.write_all(value.as_bytes())?;
        file.sync_all()?;
        fs::rename(&temporary, path(name))?;
        Ok(())
    })();
    if result.is_err() {
        let _ = fs::remove_file(temporary);
    }
    result
}

fn read_private(name: &str) -> Result<String> {
    let file = OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW)
        .open(path(name))?;
    let metadata = file.metadata()?;
    ensure!(
        metadata.is_file()
            && metadata.uid() == 0
            && metadata.mode() & 0o777 == 0o600
            && metadata.len() <= 128,
        "Invalid ownership, permissions, or size for {name}"
    );
    let mut value = String::new();
    file.take(129).read_to_string(&mut value)?;
    ensure!(value.len() <= 128, "Invalid size for {name}");
    Ok(value)
}

/// A token is exactly 64 lowercase hex characters.
fn is_token(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

fn live_token() -> Result<String> {
    // A process that merely binds port 9178 must receive no credentials.
    let pid = crate::webui_auth::server_pid(&read_private("webui.pid")?)
        .context("Invalid web manager PID")?;
    let status = fs::read_to_string(format!("/proc/{pid}/status"))?;
    let cmdline = fs::read(format!("/proc/{pid}/cmdline"))?;
    ensure!(
        crate::webui_auth::root_server_process(&status, &cmdline),
        "No authenticated root web manager is running"
    );
    let value = read_private("webui.token")?;
    ensure!(is_token(&value), "Invalid web token");
    Ok(value)
}

struct ServerIdentity {
    pid: u32,
}

impl ServerIdentity {
    fn publish() -> Result<Self> {
        let pid = std::process::id();
        write_private("webui.pid", &format!("{pid}\n"))?;
        Ok(Self { pid })
    }
}

impl Drop for ServerIdentity {
    fn drop(&mut self) {
        if read_private("webui.pid")
            .ok()
            .and_then(|value| crate::webui_auth::server_pid(&value))
            == Some(self.pid)
        {
            let _ = fs::remove_file(path("webui.pid"));
        }
    }
}

fn control(endpoint: &str) -> Result<()> {
    let secret = live_token()?;
    let mut stream = TcpStream::connect_timeout(&ADDRESS.parse()?, Duration::from_secs(1))?;
    stream.set_read_timeout(Some(Duration::from_secs(2)))?;
    stream.set_write_timeout(Some(Duration::from_secs(2)))?;
    write!(
        stream,
        "POST {endpoint} HTTP/1.1\r\nHost: {ADDRESS}\r\nAuthorization: Bearer {secret}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
    )?;
    let mut reply = String::new();
    stream.take(4096).read_to_string(&mut reply)?;
    ensure!(
        reply.starts_with("HTTP/1.1 200"),
        "Web manager did not accept the request"
    );
    Ok(())
}

fn installed_daemon() -> Result<&'static str> {
    let metadata = fs::symlink_metadata(defs::DAEMON_PATH)
        .context("Install or update ksud from the Manager before starting the web service")?;
    ensure!(
        metadata.is_file()
            && metadata.uid() == 0
            && metadata.mode() & 0o022 == 0
            && metadata.mode() & 0o111 != 0
            && metadata.len() > 0,
        "Installed ksud must be a root-owned, protected executable file"
    );
    Ok(defs::DAEMON_PATH)
}

fn process_identity(stat: &str) -> Option<(u64, bool)> {
    // The parenthesized comm field may contain spaces or closing parentheses.
    let (_, fields) = stat.rsplit_once(')')?;
    let mut fields = fields.split_whitespace();
    let state = fields.next()?;
    // starttime is field 22; state above is field 3.
    let starttime = fields.nth(18)?.parse().ok()?;
    Some((starttime, state != "Z" && state != "X"))
}

fn wait_for_shutdown(pid: u32, starttime: u64) -> Result<()> {
    // A loaded device can take a while to tear the previous server down: the
    // HTTP acknowledgement precedes the shutdown, and tiny_http closes its
    // listener on another thread. Too short a deadline turns a normal restart
    // into a spurious "did not stop" failure.
    let deadline = Instant::now() + Duration::from_secs(5);
    let address = ADDRESS.parse()?;
    loop {
        let exited = match fs::read_to_string(format!("/proc/{pid}/stat")) {
            Ok(stat) => {
                let (current_starttime, running) =
                    process_identity(&stat).context("Invalid web manager process status")?;
                current_starttime != starttime || !running
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => true,
            Err(error) => return Err(error).context("Cannot inspect the previous web manager"),
        };
        let released = match TcpStream::connect_timeout(&address, Duration::from_millis(100)) {
            Ok(stream) => {
                let _ = stream.shutdown(std::net::Shutdown::Both);
                false
            }
            Err(error) => error.kind() == std::io::ErrorKind::ConnectionRefused,
        };
        if exited && released {
            return Ok(());
        }
        ensure!(
            Instant::now() < deadline,
            "Previous web manager did not stop or release port 9178; open the Manager, stop its web service, then retry"
        );
        std::thread::sleep(Duration::from_millis(50));
    }
}

pub fn start() -> Result<()> {
    if control("/api/ping").is_ok() {
        let pid = crate::webui_auth::server_pid(&read_private("webui.pid")?)
            .context("Invalid web manager PID")?;
        let running = fs::metadata(format!("/proc/{pid}/exe"))?;
        let installed = fs::metadata(installed_daemon()?)?;
        if running.dev() == installed.dev() && running.ino() == installed.ino() {
            return Ok(());
        }
        let (starttime, _) = process_identity(&fs::read_to_string(format!("/proc/{pid}/stat"))?)
            .context("Invalid web manager process status")?;
        // An APK/daemon update must not silently keep the previous server alive.
        // `/api/restart` exits without revoking, so an update keeps the saved
        // link working; only a user-facing stop revokes.
        control("/api/restart")?;
        // The HTTP acknowledgement precedes shutdown, and tiny_http closes its
        // listener on another thread. Wait for both before binding a new one.
        wait_for_shutdown(pid, starttime)?;
    } else if live_token().is_ok() {
        bail!(
            "Existing root web manager is not responding; open the Manager to unfreeze it, stop its web service, then retry"
        );
    }
    prepare_directory()?;
    let log = OpenOptions::new()
        .create(true)
        .append(true)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(path("webui.log"))?;
    ensure!(
        log.metadata()?.uid() == 0,
        "Web manager log must be owned by root"
    );
    log.set_permissions(fs::Permissions::from_mode(0o600))?;
    // Use the installed executable, whose path survives APK replacement and
    // launcher changes. The Manager installs its current daemon on startup.
    let mut child = Command::new(installed_daemon()?);
    child
        .args(["webui", "serve"])
        .current_dir("/")
        .stdin(Stdio::null())
        .stdout(log.try_clone()?)
        .stderr(log);
    unsafe {
        child.pre_exec(|| {
            if libc::setsid() < 0 {
                Err(std::io::Error::last_os_error())
            } else {
                Ok(())
            }
        });
    }
    let mut child = child
        .spawn()
        .context("Failed to start the installed web manager daemon")?;
    for _ in 0..30 {
        std::thread::sleep(Duration::from_millis(100));
        if control("/api/ping").is_ok() {
            return Ok(());
        }
        if let Some(status) = child.try_wait()? {
            bail!(
                "Installed ksud web manager exited with {status}{}",
                log_tail()
            );
        }
    }
    bail!("Web manager failed to start{}", log_tail())
}

/// Tail of the web manager log, formatted for appending to an error message.
///
/// The daemon writes its real reason for exiting (a UAPI mismatch, a port
/// already in use, a missing driver fd) to this log, and the Manager shows only
/// the first line of ksud's stderr. Without this the user sees a generic
/// "failed to start" and the cause stays buried on the device.
fn log_tail() -> String {
    let Ok(mut file) = File::open(path("webui.log")) else {
        return "; check /data/adb/ksu/webui.log".to_owned();
    };
    let mut buffer = String::new();
    if file.read_to_string(&mut buffer).is_err() || buffer.trim().is_empty() {
        return "; check /data/adb/ksu/webui.log".to_owned();
    }
    let line = buffer
        .lines()
        .rev()
        .map(str::trim)
        .find(|line| !line.is_empty())
        .unwrap_or_default();
    if line.is_empty() {
        "; check /data/adb/ksu/webui.log".to_owned()
    } else {
        format!(": {line}")
    }
}

/// Stop the service and revoke the browser link it handed out.
///
/// The token deliberately survives restarts, reboots, and daemon replacement so
/// a saved link keeps working without a fresh `webui url` from the Manager. An
/// explicit stop is therefore the one action that invalidates it.
fn stop() -> Result<()> {
    // A running server holds the secret in memory, so deleting the file alone
    // would leave a leaked link working. Shutting it down is therefore required
    // while it is up. When nothing is running (a reboot with autostart off, or a
    // stop from the panel) there is no in-memory secret left to invalidate, and
    // removing the file is the whole job.
    if live_token().is_ok() {
        control("/api/shutdown")?;
    }
    match fs::remove_file(path("webui.token")) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(error).context("Cannot revoke the web manager token"),
    }
}

/// Revoke the link handed out so far and hand out a fresh one.
///
/// Revocation is real only once the running server has exited and its token
/// file is gone: from that point a saved or leaked link stops working. The
/// replacement then mints a new token (see `serve`), and the caller gets the
/// new URL.
fn rotate() -> Result<()> {
    // A stale PID file (a reboot while autostart was disabled) is harmless: the
    // token removal below still invalidates any saved link.
    if let Some(pid) = read_private("webui.pid")
        .ok()
        .and_then(|value| crate::webui_auth::server_pid(&value))
        && let Ok(status) = fs::read_to_string(format!("/proc/{pid}/stat"))
        && let Some((starttime, _)) = process_identity(&status)
    {
        control("/api/shutdown")?;
        wait_for_shutdown(pid, starttime)?;
    }
    match fs::remove_file(path("webui.token")) {
        Ok(()) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error).context("Cannot revoke the web manager token"),
    }
    start()?;
    println!("{ORIGIN}/#{}", live_token()?);
    Ok(())
}

pub fn run(command: WebCommand) -> Result<()> {
    ensure!(
        unsafe { libc::geteuid() } == 0,
        "Root is required for the web manager"
    );
    match command {
        WebCommand::Start => start(),
        WebCommand::Stop => stop(),
        WebCommand::Rotate => rotate(),
        WebCommand::Url => {
            println!("{ORIGIN}/#{}", live_token()?);
            Ok(())
        }
        WebCommand::Autostart { mode } => {
            match mode.as_str() {
                "on" => {
                    prepare_directory()?;
                    fs::write(path("webui.autostart"), b"1")?;
                }
                "off" => {
                    if autostart_enabled() {
                        fs::remove_file(path("webui.autostart"))?;
                    }
                }
                _ => println!("{}", autostart_enabled()),
            }
            Ok(())
        }
        WebCommand::Serve => serve(),
        WebCommand::Grant {
            package,
            uid,
            granted,
        } => set_grant_as_manager(&package, uid, granted),
    }
}

fn set_grant_as_manager(package: &str, uid: i32, granted: bool) -> Result<()> {
    ensure!(
        unsafe { libc::getuid() } == 0,
        "Root is required for the grant helper"
    );
    let manager = ksucalls::manager_appid()?;
    ensure!(
        (10_000..100_000).contains(&manager),
        "No authenticated Manager is installed"
    );
    let packages = fs::read_to_string("/data/system/packages.list")?;
    let package_uid = |name: &str| {
        packages.lines().find_map(|line| {
            let mut fields = line.split_whitespace();
            (fields.next()? == name)
                .then(|| fields.next()?.parse::<u32>().ok())
                .flatten()
        })
    };
    ensure!(
        package_uid(defs::DEFAULT_PACKAGE_NAME) == Some(manager),
        "Manager UID does not match the kernel identity"
    );
    ensure!(
        u32::try_from(uid).is_ok_and(|uid| package_uid(package) == Some(uid)),
        "Package/UID mismatch"
    );
    // GET/SET_APP_PROFILE requires the Manager's real UID. This short-lived
    // helper keeps effective/saved UID 0, and changes no server-thread identity.
    ensure!(
        unsafe { libc::setresuid(manager, 0, 0) } == 0,
        "Cannot assume the authenticated Manager UID: {}",
        std::io::Error::last_os_error()
    );
    ensure!(
        unsafe { libc::getuid() } == manager && unsafe { libc::geteuid() } == 0,
        "Unexpected grant helper credentials"
    );
    ksucalls::set_default_app_grant(package, uid, granted)
}

fn header<'a>(request: &'a Request, name: &'static str) -> Option<&'a str> {
    request
        .headers()
        .iter()
        .find(|h| h.field.equiv(name))
        .map(|h| h.value.as_str())
}

fn serve() -> Result<()> {
    // setsid only detaches the process group; Android can still kill the whole
    // Manager cgroup when its launcher component changes. Move this process
    // before creating HTTP worker threads, as for the existing root shell.
    crate::utils::switch_cgroups();
    ksucalls::ensure_uapi_version_matched()?;
    crate::utils::switch_mnt_ns(1)?;
    let server = Server::http(ADDRESS).map_err(|e| anyhow::anyhow!("{e}"))?;
    prepare_directory()?;
    // Mint only after binding, so a failed start never publishes a credential
    // that a fake listener could capture and replay against a future root
    // service.
    //
    // A still-valid token is reused instead of rotated. Rotating on every start
    // forced the user back into the Manager for a fresh `webui url` after each
    // restart or reboot, which is exactly the dependency on the Manager that the
    // panel must not have. `webui stop` revokes the link, and the next start
    // mints a replacement.
    let secret = match read_private("webui.token") {
        Ok(existing) if is_token(&existing) => existing,
        _ => {
            let fresh = random_token()?;
            write_private("webui.token", &fresh)?;
            fresh
        }
    };
    // Dropped before Server, withdrawing the PID before releasing its socket.
    let _identity = ServerIdentity::publish()?;
    for mut request in server.incoming_requests() {
        let endpoint = request.url().split('?').next().unwrap_or("").to_owned();
        let authenticated = header(&request, "Authorization")
            .and_then(|v| v.strip_prefix("Bearer "))
            == Some(secret.as_str());
        let allowed = crate::webui_auth::allowed(
            header(&request, "Host"),
            header(&request, "Origin"),
            header(&request, "Authorization"),
            &secret,
            endpoint == "/",
        );
        let (status, mime, body) = if !allowed {
            (
                403,
                "application/json",
                json!({"error":"Access denied; open the URL from 🐾星棱狐🐾"}).to_string(),
            )
        } else if endpoint == "/" && request.method() == &Method::Get {
            (
                200,
                "text/html; charset=utf-8",
                include_str!("webui.html").to_owned(),
            )
        } else {
            match dispatch(&mut request, &endpoint) {
                Ok(value) => (200, "application/json", value.to_string()),
                Err(err) => (
                    400,
                    "application/json",
                    json!({"error":format!("{err:#}")}).to_string(),
                ),
            }
        };
        // `/api/shutdown` is the user-facing stop, so it revokes the link.
        // `/api/restart` is the internal replacement used when the daemon
        // changes: it must exit without revoking, or every update would
        // invalidate the saved link and reintroduce the Manager dependency.
        let exit = status == 200
            && authenticated
            && matches!(endpoint.as_str(), "/api/shutdown" | "/api/restart");
        let revoke = exit && endpoint == "/api/shutdown";
        let mut response = Response::from_string(body).with_status_code(status);
        for (name, value) in [
            ("Content-Type", mime),
            ("Cache-Control", "no-store"),
            ("X-Content-Type-Options", "nosniff"),
            ("Referrer-Policy", "no-referrer"),
            ("X-Frame-Options", "DENY"),
        ] {
            response.add_header(
                Header::from_bytes(name, value)
                    .map_err(|()| anyhow::anyhow!("Invalid response header"))?,
            );
        }
        if let Err(e) = request.respond(response) {
            log::warn!("Web response failed: {e}");
        }
        if exit {
            break;
        }
    }
    if revoke {
        // Stopping is what revokes the link. Removing the token here rather
        // than only in `stop` keeps both stop entry points consistent: whether
        // the service is stopped by `ksud webui stop` or by the panel's own
        // button, the link that was handed out stops working.
        match fs::remove_file(path("webui.token")) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => log::warn!("Cannot revoke the web manager token: {error}"),
        }
    }
    Ok(())
}

fn command(program: &str, args: &[&str]) -> Result<String> {
    let output = Command::new(program).args(args).output()?;
    ensure!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    Ok(String::from_utf8(output.stdout)?)
}

fn apps() -> Result<Vec<Value>> {
    let packages = fs::read_to_string("/data/system/packages.list")?;
    let labels = crate::webui_apps::labels();
    packages
        .lines()
        .map(|line| {
            let mut fields = line.split_whitespace();
            let package = fields.next().context("Missing package")?;
            let uid: u32 = fields.next().context("Missing UID")?.parse()?;
            let metadata = labels.get(package).filter(|metadata| metadata.uid == uid);
            Ok(json!({
                "package": package,
                "uid": uid,
                "label": metadata.map_or(package, |metadata| metadata.label.as_str()),
                "system": metadata.map(|metadata| metadata.system),
                "granted": ksucalls::uid_granted_root(uid)?
            }))
        })
        .collect()
}

fn stealth() -> Result<bool> {
    let dump = command(
        "/system/bin/dumpsys",
        &["package", defs::DEFAULT_PACKAGE_NAME],
    )?;
    let mut system_user = false;
    let mut disabled = false;
    for line in dump.lines().map(str::trim) {
        if line.starts_with("User ") {
            system_user = line.starts_with("User 0:");
            disabled = false;
        }
        if system_user && line == "disabledComponents:" {
            disabled = true;
            continue;
        }
        if disabled && line == LAUNCHER {
            return Ok(true);
        }
        if disabled && line.contains(':') {
            disabled = false;
        }
    }
    Ok(false)
}

/// The dialer code that restores the hidden launcher icon, as the panel shows it.
///
/// The code is user-configurable and lives in the Manager's own preferences, so
/// the panel cannot hardcode it: showing a stale code is worse than showing
/// none, because the user would trust it and get locked out after hiding the
/// icon. Reading the preference file directly keeps the panel in step with the
/// Manager without a second source of truth or another supercall.
///
/// Returns `None` only when the file exists but cannot be read, i.e. when the
/// Manager's real value is genuinely unknown to us.
fn dial_code() -> Option<String> {
    const DEFAULT: &str = "917891786688";
    let path = format!(
        "/data/data/{}/shared_prefs/ikun_stealth.xml",
        defs::DEFAULT_PACKAGE_NAME
    );
    let code = match fs::read_to_string(&path) {
        Ok(xml) => parse_stored_code(&xml)
            .filter(|value| (4..=32).contains(&value.len()))
            .unwrap_or_else(|| DEFAULT.to_owned()),
        // No preference file means the user never changed the code, so the
        // shipped default is exactly what the Manager would report too.
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => DEFAULT.to_owned(),
        Err(_) => return None,
    };
    Some(format!("*#*#{code}#*#*"))
}

/// Extract the `<string name="recovery_code">` value from a preference XML file.
fn parse_stored_code(xml: &str) -> Option<String> {
    let marker = "name=\"recovery_code\"";
    let rest = &xml[xml.find(marker)? + marker.len()..];
    let open = rest.find('>')? + 1;
    let close = rest[open..].find('<')? + open;
    let value = &rest[open..close];
    if value.bytes().all(|byte| byte.is_ascii_digit()) {
        Some(value.to_owned())
    } else {
        None
    }
}

fn set_stealth(enabled: bool) -> Result<()> {
    if enabled {
        crate::stealth::start()?;
        // Policy changes prevent future freezes but cannot thaw an existing one.
        // Only terminate the verified frozen Manager, preserving launchability.
        if let Err(error) = crate::stealth::clear_frozen_manager() {
            crate::stealth::restore_launcher()?;
            return Err(error).context("Cannot clear the frozen Manager before hiding");
        }
    }
    let component = format!("{}/{LAUNCHER}", defs::DEFAULT_PACKAGE_NAME);
    command(
        "/system/bin/pm",
        &[
            if enabled { "disable" } else { "enable" },
            "--user",
            "0",
            &component,
        ],
    )?;
    if !enabled {
        let _ = crate::stealth::stop();
    }
    Ok(())
}

fn dispatch(request: &mut Request, endpoint: &str) -> Result<Value> {
    let read_only = [
        "/api/status",
        "/api/features",
        "/api/apps",
        "/api/superuser",
        "/api/modules",
        "/api/su_log_dump",
    ];
    ensure!(
        request.method() == &Method::Post
            || (request.method() == &Method::Get && read_only.contains(&endpoint)),
        "POST is required for changes"
    );
    if endpoint == "/api/module_install" {
        ensure!(
            request
                .body_length()
                .is_some_and(|n| n > 0 && n as u64 <= MAX_UPLOAD),
            "ZIP must be at most 256 MiB"
        );
        ensure!(
            header(request, "Content-Type") == Some("application/zip"),
            "Send the ZIP as application/zip"
        );
        let mut file = tempfile::Builder::new()
            .suffix(".zip")
            .tempfile_in(defs::WORKING_DIR)?;
        let expected = request.body_length().context("Missing upload size")? as u64;
        let copied = std::io::copy(&mut request.as_reader().take(MAX_UPLOAD + 1), &mut file)?;
        ensure!(
            copied == expected && copied <= MAX_UPLOAD,
            "Incomplete upload"
        );
        file.flush()?;
        module::install_module(file.path().to_str().context("Invalid upload path")?)?;
        return Ok(json!({"ok":true}));
    }
    let mut body = String::new();
    request
        .as_reader()
        .take(16 * 1024 + 1)
        .read_to_string(&mut body)?;
    ensure!(body.len() <= 16 * 1024, "Request too large");
    let args: Value = if body.is_empty() {
        json!({})
    } else {
        serde_json::from_str(&body)?
    };
    match endpoint {
        "/api/ping" | "/api/shutdown" => Ok(json!({"ok":true})),
        // Internal: exit without revoking the stored link. Used by `start` when
        // it must replace a running daemon after an update.
        "/api/restart" => Ok(json!({"ok":true})),
        "/api/status" => Ok(
            json!({"name":"🐾星棱狐🐾","version":defs::VERSION_NAME,"kernel":ksucalls::get_version(),"mode":ksucalls::runtime_mode(),"stealth":stealth()?,"webui_autostart":autostart_enabled(),"dial_code":dial_code()}),
        ),
        "/api/features" => {
            let managed = module::get_managed_features()?;
            let items: Vec<_> = FeatureId::ALL.iter().map(|id| {
                let state = ksucalls::get_feature(*id as u32).unwrap_or_default();
                json!({"name":id.name(),"enabled":state.value != 0,"supported":state.supported,"forced":state.forced,"managed":managed.values().any(|v| v.iter().any(|s| s == id.name()))})
            }).collect();
            Ok(json!(items))
        }
        "/api/apps" => Ok(json!(apps()?)),
        "/api/superuser" => Ok(json!(
            apps()?
                .into_iter()
                .filter(|app| app["granted"] == true)
                .collect::<Vec<_>>()
        )),
        "/api/modules" => Ok(serde_json::from_str(&command(
            defs::DAEMON_PATH,
            &["module", "list"],
        )?)?),
        "/api/superuser_toggle" => {
            let package = args["package"].as_str().context("Missing package")?;
            let uid = args["uid"].as_u64().context("Missing UID")?;
            ensure!(
                apps()?
                    .iter()
                    .any(|v| v["package"] == package && v["uid"] == uid),
                "Package/UID mismatch"
            );
            let uid = i32::try_from(uid)?.to_string();
            let granted = args["granted"].as_bool().context("Missing granted")?;
            command(
                installed_daemon()?,
                &[
                    "webui",
                    "grant",
                    package,
                    &uid,
                    "--granted",
                    if granted { "true" } else { "false" },
                ],
            )?;
            Ok(json!({"ok":true}))
        }
        "/api/feature_toggle" => {
            let name = args["name"].as_str().context("Missing feature")?;
            let enabled = args["enabled"].as_bool().context("Missing enabled")?;
            match name {
                "stealth" => set_stealth(enabled)?,
                "webui_autostart" => run(WebCommand::Autostart {
                    mode: if enabled { "on" } else { "off" }.to_owned(),
                })?,
                _ => {
                    feature::set_feature(name, u64::from(enabled))?;
                    feature::save_config()?;
                }
            }
            Ok(json!({"ok":true}))
        }
        "/api/feature_save" => {
            feature::save_config()?;
            Ok(json!({"ok":true}))
        }
        "/api/module_action" => {
            let id = args["id"].as_str().context("Missing module ID")?;
            module::validate_module_id(id)?;
            match args["act"].as_str().context("Missing action")? {
                "enable" => module::enable_module(id)?,
                "disable" => module::disable_module(id)?,
                "uninstall" => module::uninstall_module(id)?,
                "undo" => module::undo_uninstall_module(id)?,
                "action" => module::run_action(id)?,
                _ => bail!("Unknown module action"),
            }
            Ok(json!({"ok":true}))
        }
        "/api/su_log_dump" => {
            let mut logs: Vec<_> = fs::read_dir(defs::LOG_DIR)?
                .filter_map(std::result::Result::ok)
                .map(|e| e.path())
                .filter(|p| {
                    p.file_name().is_some_and(|n| {
                        n.to_string_lossy().starts_with("sulog-")
                            && n.to_string_lossy().ends_with(".log")
                    })
                })
                .collect();
            logs.sort();
            let log = if let Some(latest) = logs.last() {
                use std::io::{Seek, SeekFrom};
                let mut file = File::open(latest)?;
                let length = file.metadata()?.len();
                file.seek(SeekFrom::Start(length.saturating_sub(256 * 1024)))?;
                let mut bytes = Vec::new();
                file.take(256 * 1024).read_to_end(&mut bytes)?;
                String::from_utf8_lossy(&bytes).into_owned()
            } else {
                String::new()
            };
            Ok(json!({"log":log}))
        }
        _ => bail!("Unknown API endpoint"),
    }
}
