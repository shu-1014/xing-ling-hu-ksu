package com.xinglinghu.manager.ui.screen.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import com.xinglinghu.manager.BuildConfig
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.LocalUiMode
import com.xinglinghu.manager.ui.UiMode
import com.xinglinghu.manager.ui.navigation3.LocalNavigator

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val qqGroup = stringResource(R.string.qq_group_number)
    val copiedMessage = stringResource(R.string.about_qq_group_copied)
    val state = AboutUiState(
        title = stringResource(R.string.about),
        appName = stringResource(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        // No outbound links: the release channel is shown as a copyable value
        // so the app never hands the user off to a third-party site.
        contacts = listOf(
            ContactInfo(
                title = stringResource(R.string.about_qq_group),
                value = qqGroup,
                summary = stringResource(R.string.about_qq_group_summary),
            ),
        ),
    )
    val actions = AboutScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onCopyContact = { value ->
            context.copyToClipboard(value)
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutScreenMiuix(state, actions)
        UiMode.Material -> AboutScreenMaterial(state, actions)
    }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("xinglinghu", text))
}
