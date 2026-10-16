package com.xinglinghu.manager.ui.screen.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat

@Immutable
data class ManagerVersion(
    val versionName: String,
    val versionCode: Long
)

@Immutable
data class SystemInfo(
    val kernelVersion: String,
    val managerVersion: String,
    val deviceModel: String,
    val fingerprint: String,
    val selinuxStatus: String,
    val seccompStatus: Int,
    val zygiskImplementation: String? = null,
)

/**
 * Detects which Zygisk implementation is installed (if any) by scanning the
 * `ksud module list` output for modules flagged as Zygisk providers, and
 * reports its daemon status. Returns [notInstalledText] when nothing is found.
 */
fun getZygiskImplementation(
    notInstalledText: String,
    disabledText: String,
    rebootRequiredText: String,
): String {
    return runCatching {
        val modulesJson = com.xinglinghu.manager.ui.util.listModules()
        val array = org.json.JSONArray(modulesJson)
        val providers = mutableListOf<org.json.JSONObject>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val isProvider = (obj.optInt("zygisk_provider") != 0) || obj.optBoolean("zygisk_provider")
            if (isProvider) {
                providers.add(obj)
            }
        }

        if (providers.isEmpty()) {
            return notInstalledText
        }

        // 1. Check if any provider's daemon is actively running
        val runningProvider = providers.firstOrNull {
            (it.optInt("zygisk_running") != 0) || it.optBoolean("zygisk_running")
        }
        if (runningProvider != null) {
            val name = runningProvider.optString("name").takeIf { it.isNotBlank() }
                ?: runningProvider.getString("id")
            val version = runningProvider.optString("version").takeIf { it.isNotBlank() }
            return listOfNotNull(name, version).joinToString(" ")
        }

        // 2. If none is running, check enabled status
        val enabledProviders = providers.filter {
            it.optBoolean("enabled", false) && !it.optBoolean("remove", false)
        }
        if (enabledProviders.isNotEmpty()) {
            val names = enabledProviders.joinToString(", ") {
                it.optString("name").takeIf { name -> name.isNotBlank() } ?: it.getString("id")
            }
            return "$names ($rebootRequiredText)"
        }

        // 3. All installed providers are disabled
        val names = providers.joinToString(", ") {
            it.optString("name").takeIf { name -> name.isNotBlank() } ?: it.getString("id")
        }
        return "$names ($disabledText)"
    }.getOrDefault(notInstalledText)
}

fun getManagerVersion(context: Context): ManagerVersion {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)!!
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    return ManagerVersion(
        versionName = packageInfo.versionName!!,
        versionCode = versionCode
    )
}
