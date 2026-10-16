package com.xinglinghu.manager.ui.stealth

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object StealthController {
    /**
     * The code as typed in the dialer, e.g. `*#*#1234#*#*`.
     *
     * A function rather than a constant because the code is user-configurable;
     * the old constant silently pinned the UI to a value the receiver no longer
     * had to agree with.
     */
    fun dialCode(context: Context): String = DialerRecoveryCode.dialCode(context)

    fun recoveryCode(context: Context): String = DialerRecoveryCode.value(context)

    private fun launcher(context: Context) =
        ComponentName(context, "com.xinglinghu.manager.Launcher")

    fun isHidden(context: Context) =
        context.packageManager.getComponentEnabledSetting(launcher(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setHidden(context: Context, hidden: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            launcher(context),
            if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}
