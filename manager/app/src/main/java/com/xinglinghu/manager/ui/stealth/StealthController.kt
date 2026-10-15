package com.xinglinghu.manager.ui.stealth

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object StealthController {
    const val RECOVERY_CODE = DialerRecoveryCode.VALUE
    const val DIAL_CODE = "*#*#$RECOVERY_CODE#*#*"
    private fun launcher(context: Context) = ComponentName(context, "com.xinglinghu.manager.Launcher")
    fun isHidden(context: Context) = context.packageManager.getComponentEnabledSetting(launcher(context)) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    fun setHidden(context: Context, hidden: Boolean) {
        context.packageManager.setComponentEnabledSetting(launcher(context), if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }
}
