package com.xinglinghu.manager.ui.cosmetic

import android.content.Context
import java.security.MessageDigest

/**
 * "Has the announcement been seen?" state for the launch popup.
 *
 * ADAPTED from 7kimisu's `AnnouncementPrefs`. Their copy reads a separate
 * `settings` preferences file because their Manager has a settings repository;
 * this fork keeps everything appearance-related in the one `ikun_cosmetic` store
 * so there is a single place to clear.
 *
 * The announcement itself is a local string resource. This fork deliberately has
 * no update check (removed by request in v3.4.0), so nothing here touches the
 * network or the kernel — it is pure in-memory preference reads on the cold-start
 * path.
 */
object AnnouncementPrefs {

    private const val PREFS = "ikun_cosmetic"
    private const val KEY_NEVER_SHOW = "announcement_never_show"
    private const val KEY_SEEN = "announcement_seen_key"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once the user turns off the "show announcement" switch. */
    fun neverShow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NEVER_SHOW, false)

    fun setNeverShow(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_NEVER_SHOW, value).apply()
    }

    /** Fingerprint of the last announcement the user dismissed. */
    fun seenKey(context: Context): String =
        prefs(context).getString(KEY_SEEN, "").orEmpty()

    fun markSeen(context: Context, key: String) {
        prefs(context).edit().putString(KEY_SEEN, key).apply()
    }

    /**
     * Content fingerprint = first 8 bytes of SHA-256 over title + body.
     *
     * Using a content hash instead of a hand-maintained version constant means
     * editing the announcement text automatically re-arms it exactly once, with
     * nobody needing to remember to bump a number.
     */
    fun keyOf(title: String, content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$title\n$content".toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
