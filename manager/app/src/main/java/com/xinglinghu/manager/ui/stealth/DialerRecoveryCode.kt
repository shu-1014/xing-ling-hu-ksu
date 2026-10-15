package com.xinglinghu.manager.ui.stealth

object DialerRecoveryCode {
    const val VALUE = "917891786688"
    const val MODERN_ACTION = "android.telephony.action.SECRET_CODE"
    const val LEGACY_ACTION = "android.provider.Telephony.SECRET_CODE"

    fun matches(action: String?, data: String?): Boolean =
        (action == MODERN_ACTION || action == LEGACY_ACTION) &&
            data == "android_secret_code://$VALUE"
}
