package com.xinglinghu.manager.ui.stealth

import android.content.Context

/**
 * The dialer code that restores the hidden launcher icon.
 *
 * The code is user-configurable, so it cannot live in the manifest: an
 * `<intent-filter>` host is fixed at install time and only the installer could
 * ever change it. The manifest therefore declares the two SECRET_CODE actions
 * with the `android_secret_code` scheme and *no* host, which makes Android
 * deliver every dialed secret code to the receiver; the real comparison happens
 * here, at runtime, against the stored value.
 *
 * That is also why the code is normalized on the way in and on the way out. The
 * dialer only ever sends `android_secret_code://<code>` with the raw digits, so
 * a stored value containing `*`, `#` or whitespace could never match anything.
 */
object DialerRecoveryCode {
    const val MODERN_ACTION = "android.telephony.action.SECRET_CODE"
    const val LEGACY_ACTION = "android.provider.Telephony.SECRET_CODE"

    /** Shipped default, kept stable so existing users' habit keeps working. */
    const val DEFAULT_VALUE = "917891786688"

    private const val PREFS = "ikun_stealth"
    private const val KEY_CODE = "recovery_code"

    /** Longest accepted code; the dialer's own buffer is the real limit. */
    private const val MAX_LENGTH = 32

    /**
     * The code currently in effect.
     *
     * Falls back to [DEFAULT_VALUE] when storage holds nothing usable, so a
     * corrupt preference can never leave the user locked out with no code.
     */
    fun value(context: Context): String {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CODE, null)
        return stored?.takeIf { isValid(it) } ?: DEFAULT_VALUE
    }

    /** Store [code] after sanitizing; returns false when nothing usable remains. */
    fun setValue(context: Context, code: String): Boolean {
        val normalized = normalize(code)
        if (!isValid(normalized)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE, normalized)
            .apply()
        return true
    }

    /** Restore the shipped default. */
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CODE)
            .apply()
    }

    /** The code as the user types it in the dialer, e.g. `*#*#1234#*#*`. */
    fun dialCode(context: Context): String = "*#*#" + value(context) + "#*#*"

    /**
     * Keep digits only. The dialer's secret-code URI never carries separators,
     * so storing them would silently produce a code that can never fire.
     */
    fun normalize(code: String): String = code.filter { it.isDigit() }.take(MAX_LENGTH)

    /** A usable code is short enough to type and not trivially guessable. */
    fun isValid(code: String): Boolean = code.length in 4..MAX_LENGTH && code.all { it.isDigit() }

    /**
     * True when [data] is the secret-code URI for the user's current code.
     *
     * The host is compared rather than the whole string so a trailing slash or
     * an empty path survives, which some dialers append.
     */
    fun matches(context: Context, action: String?, data: String?): Boolean {
        if (action != MODERN_ACTION && action != LEGACY_ACTION) return false
        val host = data
            ?.removePrefix("android_secret_code://")
            ?.substringBefore('/')
            ?.substringBefore('?')
            ?: return false
        return host == value(context)
    }
}
