package com.xinglinghu.manager.webui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/** Read-only app_process entry point used by the root web manager. */
public final class WebAppLabels {
    private static final int MAX_APPS = 10000;
    private static final int MAX_LABEL_CODE_POINTS = 256;
    private static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;

    private WebAppLabels() {}

    @SuppressWarnings("deprecation") // app_process has no framework-created main looper.
    public static void main(String[] args) {
        try {
            if (Looper.getMainLooper() == null) {
                Looper.prepareMainLooper();
            }
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getDeclaredMethod("systemMain").invoke(null);
            Context context = (Context) activityThread.getDeclaredMethod("getSystemContext").invoke(thread);
            Configuration configuration = configuration(context, args);
            Context localizedContext = context.createConfigurationContext(configuration);
            PackageManager pm = localizedContext.getPackageManager();
            List<ApplicationInfo> applications = pm.getInstalledApplications(0);
            if (applications.size() > MAX_APPS) {
                throw new IllegalStateException("Installed application count exceeds limit");
            }
            applications.sort(Comparator.comparing(info -> info.packageName));
            JSONArray apps = new JSONArray();
            for (ApplicationInfo info : applications) {
                // The web authorization list uses packages.list's primary-user UIDs.
                if (info.uid < 0 || info.uid >= 100000) {
                    continue;
                }
                JSONObject app = new JSONObject();
                app.put("package", info.packageName);
                app.put("uid", info.uid);
                app.put("label", label(info, pm, localizedContext, configuration));
                app.put("system", (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0);
                apps.put(app);
            }
            JSONObject result = new JSONObject();
            result.put("schema", 1);
            result.put("user", 0);
            result.put("locale", configuration.getLocales().toLanguageTags());
            result.put("apps", apps);
            byte[] output = result.toString().getBytes(StandardCharsets.UTF_8);
            if (output.length > MAX_OUTPUT_BYTES) {
                throw new IllegalStateException("Application metadata exceeds limit");
            }
            System.out.write(output);
            System.out.write('\n');
            System.out.flush();
            // Binder/ActivityThread workers must not keep the one-shot helper alive.
            System.exit(0);
        } catch (Exception | LinkageError error) {
            System.err.println("Unable to read installed application labels: " + error.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static Configuration configuration(Context context, String[] args) {
        Configuration result = new Configuration(context.getResources().getConfiguration());
        try {
            Class<?> activityManager = Class.forName("android.app.ActivityManager");
            Object service = activityManager.getDeclaredMethod("getService").invoke(null);
            Class<?> serviceInterface = Class.forName("android.app.IActivityManager");
            Configuration current = (Configuration) serviceInterface.getMethod("getConfiguration").invoke(service);
            if (current != null) {
                result.setTo(current);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // OEM hidden-API differences can still use the explicit primary-user locale.
        }
        if (args.length == 1 && args[0].length() <= 256 && args[0].matches("[A-Za-z0-9,-]+")) {
            LocaleList locales = LocaleList.forLanguageTags(args[0]);
            if (!locales.isEmpty()) {
                result.setLocales(locales);
            }
        }
        if (!result.getLocales().isEmpty()) {
            LocaleList.setDefault(result.getLocales());
        }
        return result;
    }

    private static String label(ApplicationInfo info, PackageManager pm, Context context, Configuration configuration) {
        CharSequence label = info.packageName;
        try {
            label = info.loadLabel(pm);
            if (info.nonLocalizedLabel == null && info.labelRes != 0) {
                // app_process is not a normal app, so explicitly select the current locale.
                // Flags 0 read APK resources without loading any third-party executable code.
                Context application = context.createPackageContext(info.packageName, 0);
                label = application.createConfigurationContext(configuration).getResources().getText(info.labelRes);
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // A broken or concurrently removed APK cannot prevent other apps being listed.
        }
        if (label == null) {
            return info.packageName;
        }
        String text = label.toString().trim();
        StringBuilder safe = new StringBuilder();
        int count = 0;
        for (int offset = 0; offset < text.length() && count < MAX_LABEL_CODE_POINTS; count++) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isISOControl(codePoint)) {
                safe.appendCodePoint(codePoint);
            }
        }
        return safe.length() == 0 ? info.packageName : safe.toString();
    }
}
