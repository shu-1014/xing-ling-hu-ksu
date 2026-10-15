package com.xinglinghu.manager.ui.cosmetic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.component.dialog.rememberConfirmDialog

/**
 * Launch announcement sheet.
 *
 * ADAPTED from 7kimisu's `AnnouncementPopup`, keeping their dismissal rules:
 *
 *  1. Only evaluated on a fresh process start ([freshStart] is wired to
 *     `savedInstanceState == null`), so a configuration change such as a theme
 *     switch does not pop it again.
 *  2. The "show announcement" switch in the personalization screen turns it off
 *     permanently.
 *  3. Otherwise it appears only when the text has changed since the last time it
 *     was dismissed, compared by content fingerprint.
 *  4. It is not suppressed in stealth mode: an announcement is something a user
 *     without root would see too, so hiding it would be a tell rather than a
 *     disguise.
 *
 * The seen marker is written only when the user dismisses the sheet. Killing the
 * app while it is open shows it once more next launch, which errs on the side of
 * the user actually reading it.
 */
@Composable
fun AnnouncementPopup(freshStart: Boolean) {
    if (!freshStart) return

    val context = LocalContext.current
    val title = stringResource(R.string.announcement_title)
    val body = stringResource(R.string.announcement_body)

    // Fingerprint is computed once; recomposition must not re-hash.
    val key = remember(title, body) { AnnouncementPrefs.keyOf(title, body) }
    val shouldShow = remember(key) {
        !AnnouncementPrefs.neverShow(context) && AnnouncementPrefs.seenKey(context) != key
    }
    if (!shouldShow) return

    val confirm = stringResource(R.string.announcement_confirm)
    val dialog = rememberConfirmDialog(
        onConfirm = { AnnouncementPrefs.markSeen(context, key) },
        onDismiss = { AnnouncementPrefs.markSeen(context, key) },
    )

    LaunchedEffect(key) {
        dialog.showConfirm(
            title = title,
            content = body,
            markdown = true,
            confirm = confirm,
        )
    }
}
