package com.xinglinghu.manager.ui.stealth;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Root-only vendor policy guard; the installed manifest receiver handles secret codes. */
public final class DialerRecoveryGuard {
    private static final String PACKAGE = "com.xinglinghu.manager";
    private static final String LAUNCHER = "com.xinglinghu.manager.Launcher";
    private static final String KEY = "MILLET_NO_RESTRICT_APP";
    private static final int MAX_VALUE = 64 * 1024;
    private static final int MAX_PACKAGES = 10000;
    private static final long POLL_MILLIS = 5000;
    private static final long HIDE_GRACE_MILLIS = 30000;

    private final Context context;
    private final Handler handler;
    private final ComponentName launcher = new ComponentName(PACKAGE, LAUNCHER);
    private final long started = SystemClock.elapsedRealtime();
    private boolean wasHidden;
    private DialerRecoveryGuard(Context context) {
        this.context = context;
        handler = new Handler(Looper.getMainLooper());
    }

    @SuppressWarnings("deprecation") // Standalone app_process needs a framework main Looper.
    public static void main(String[] args) {
        DialerRecoveryGuard guard = null;
        try {
            if (Os.getuid() != 0 || Os.geteuid() != 0 || args.length != 2
                    || !PACKAGE.equals(args[0]) || !args[1].matches("[0-9a-f]{64}")) {
                throw new SecurityException("Invalid root recovery guard identity");
            }
            escapeAppCgroups();
            if (!isMiui()) {
                throw new IllegalStateException("Recovery policy is not available on this system");
            }
            Looper.prepareMainLooper();
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("systemMain").invoke(null);
            Method getContext = activityThread.getDeclaredMethod("getSystemContext");
            getContext.setAccessible(true);
            guard = new DialerRecoveryGuard((Context) getContext.invoke(thread));
            guard.prepare();
            System.out.println("READY " + args[1]);
            System.out.flush();
            guard.handler.postDelayed(guard::pollLauncher, POLL_MILLIS);
            Looper.loop();
            throw new IllegalStateException("Recovery policy loop stopped unexpectedly");
        } catch (Throwable error) {
            if (guard != null) {
                guard.fail(error);
                return;
            }
            System.err.println("Recovery policy unavailable: " + error.getClass().getSimpleName());
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static boolean isMiui() throws Exception {
        Class<?> properties = Class.forName("android.os.SystemProperties");
        Method get = properties.getMethod("get", String.class);
        return !((String) get.invoke(null, "ro.miui.ui.version.name")).isEmpty()
                || !((String) get.invoke(null, "ro.mi.os.version.name")).isEmpty();
    }

    private static void escapeAppCgroups() throws Exception {
        byte[] pid = (Process.myPid() + "\n").getBytes(StandardCharsets.US_ASCII);
        boolean moved = writeCgroup("/sys/fs/cgroup/cgroup.procs", pid);
        moved |= writeCgroup("/acct/cgroup.procs", pid);
        moved |= writeCgroup("/dev/cg2_bpf/cgroup.procs", pid);
        writeCgroup("/dev/memcg/apps/cgroup.procs", pid);
        if (!moved) {
            throw new IllegalStateException("Cannot move recovery guard out of app cgroups");
        }
        try (InputStream membership = new FileInputStream("/proc/self/cgroup")) {
            ByteArrayOutputStream value = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = membership.read(buffer)) != -1) {
                if (value.size() + count > 4096) {
                    throw new IllegalStateException("Recovery guard cgroup state is too large");
                }
                value.write(buffer, 0, count);
            }
            if (value.toString(StandardCharsets.UTF_8.name()).contains("/uid_")) {
                throw new IllegalStateException("Recovery guard is still in an app cgroup");
            }
        }
    }

    private static boolean writeCgroup(String path, byte[] pid) {
        FileDescriptor fd = null;
        try {
            fd = Os.open(path, OsConstants.O_WRONLY | OsConstants.O_CLOEXEC
                    | OsConstants.O_NOFOLLOW, 0);
            return Os.write(fd, pid, 0, pid.length) == pid.length;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (fd != null) {
                try {
                    Os.close(fd);
                } catch (Exception ignored) {
                    // The process is exiting or the controller is unavailable.
                }
            }
        }
    }

    private void prepare() throws Exception {
        context.getPackageManager().getActivityInfo(launcher,
                PackageManager.MATCH_DISABLED_COMPONENTS);
        command(4096, "/system/bin/cmd", "appops", "set", "--user", "0",
                PACKAGE, "10008", "allow");
        // Current Android requires an AMS ProcessRecord for Settings providers
        // and ContentObserver. A root app_process has none, so use the supported
        // settings command and a bounded poll rather than report a fake observer.
        ensureMembership();
        if (!contains(readSetting())) {
            throw new IllegalStateException("Recovery policy membership was not retained");
        }
        wasHidden = isHidden();
    }

    private void ensureMembership() throws Exception {
        String before = readSetting();
        if (contains(before)) {
            return;
        }
        String updated = before == null || before.isEmpty() ? PACKAGE
                : before + (before.endsWith(",") ? "" : ",") + PACKAGE;
        validate(updated);
        command(4096, "/system/bin/settings", "--user", "0", "put", "system", KEY, updated);
        if (!contains(readSetting())) {
            throw new IllegalStateException("Recovery policy verification failed");
        }
    }

    private String readSetting() throws Exception {
        String value = command(MAX_VALUE + 2, "/system/bin/settings", "--user", "0",
                "get", "system", KEY);
        while (value.endsWith("\n") || value.endsWith("\r")) {
            value = value.substring(0, value.length() - 1);
        }
        if ("null".equals(value)) {
            value = null;
        }
        validate(value);
        return value;
    }

    private static void validate(String value) {
        if (value == null) {
            return;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE
                || value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Recovery policy exceeds its bounds");
        }
        int entries = 1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == ',' && ++entries > MAX_PACKAGES) {
                throw new IllegalArgumentException("Too many recovery policy entries");
            }
        }
    }

    private static boolean contains(String value) {
        if (value == null) {
            return false;
        }
        for (String entry : value.split(",", -1)) {
            if (PACKAGE.equals(entry.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isHidden() {
        int state = context.getPackageManager().getComponentEnabledSetting(launcher);
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
    }

    private void pollLauncher() {
        try {
            boolean hidden = isHidden();
            wasHidden |= hidden;
            if (!hidden && (wasHidden
                    || SystemClock.elapsedRealtime() - started >= HIDE_GRACE_MILLIS)) {
                System.exit(0);
                return;
            }
            ensureMembership();
            handler.postDelayed(this::pollLauncher, POLL_MILLIS);
        } catch (Exception error) {
            fail(error);
        }
    }

    private void fail(Throwable error) {
        System.err.println("Recovery policy guard failed: " + error.getClass().getSimpleName());
        error.printStackTrace(System.err);
        // Leave a visible recovery route if a running policy guard loses access.
        try {
            command(4096, "/system/bin/pm", "enable", "--user", "0", PACKAGE + "/" + LAUNCHER);
        } catch (Exception restoreError) {
            System.err.println("Launcher recovery failed: " + restoreError.getClass().getSimpleName());
        }
        System.exit(1);
    }

    private static String command(int limit, String... args) throws Exception {
        java.lang.Process child = new ProcessBuilder(args).redirectErrorStream(true).start();
        long deadline = SystemClock.elapsedRealtime() + 5000;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (InputStream output = child.getInputStream()) {
            while (child.isAlive() || output.available() > 0) {
                if (SystemClock.elapsedRealtime() >= deadline) {
                    throw new IllegalStateException("Recovery policy command timed out");
                }
                int available = output.available();
                if (available > 0) {
                    int count = output.read(buffer, 0, Math.min(available, buffer.length));
                    if (count > 0) {
                        if (result.size() + count > limit) {
                            throw new IllegalStateException("Recovery policy command output is too large");
                        }
                        result.write(buffer, 0, count);
                    }
                } else {
                    Thread.sleep(10);
                }
            }
            if (!child.waitFor(1, TimeUnit.SECONDS) || child.exitValue() != 0) {
                throw new IllegalStateException("Recovery policy command failed");
            }
            return result.toString(StandardCharsets.UTF_8.name());
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }
    }
}
