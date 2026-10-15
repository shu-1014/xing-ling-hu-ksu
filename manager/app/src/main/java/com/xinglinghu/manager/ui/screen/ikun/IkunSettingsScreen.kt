package com.xinglinghu.manager.ui.screen.ikun

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.LocalUiMode
import com.xinglinghu.manager.ui.UiMode
import com.xinglinghu.manager.ui.cosmetic.LocalCosmeticState
import com.xinglinghu.manager.ui.navigation3.LocalNavigator
import com.xinglinghu.manager.ui.stealth.StealthController
import com.xinglinghu.manager.ui.util.execKsud
import com.xinglinghu.manager.ui.util.getRootShell

internal data class IkunSettingsUiState(
    val falling: Boolean,
    val snow: Boolean,
    val particleScale: Float,
    val speed: Float,
    val hidden: Boolean,
    val recoveryCode: String,
    val autostart: Boolean,
    val busy: Boolean,
    val message: String,
)

internal data class IkunSettingsActions(
    val onBack: () -> Unit,
    val onSetFalling: (Boolean) -> Unit,
    val onSetSnow: (Boolean) -> Unit,
    val onSetParticleScale: (Float) -> Unit,
    val onSetSpeed: (Float) -> Unit,
    val onCopyCode: () -> Unit,
    val onOpenDialer: () -> Unit,
    val onSetHidden: (Boolean) -> Unit,
    val onOpenWebManager: () -> Unit,
    val onStopWebManager: () -> Unit,
    val onSetAutostart: (Boolean) -> Unit,
)

@Composable
fun IkunSettingsScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val effects = LocalCosmeticState.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var hidden by remember { mutableStateOf(StealthController.isHidden(context)) }
    var autostart by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val failed = stringResource(R.string.ikun_operation_failed)
    val done = stringResource(R.string.ikun_operation_done)
    val copied = stringResource(R.string.ikun_code_copied)
    val dialerUnavailable = stringResource(R.string.ikun_dialer_unavailable)
    val recoveryUnavailable = stringResource(R.string.ikun_recovery_unavailable)

    LifecycleResumeEffect(Unit) {
        hidden = StealthController.isHidden(context)
        scope.launch {
            autostart = withContext(Dispatchers.IO) {
                runCatching {
                    getRootShell().newJob().add("test -f /data/adb/ksu/webui.autostart").exec().isSuccess
                }.getOrDefault(false)
            }
        }
        onPauseOrDispose { }
    }

    fun operation(args: String, open: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    check(execKsud(args)) { failed }
                    if (open) {
                        val daemon = context.applicationInfo.nativeLibraryDir + "/libksud.so"
                        val result = getRootShell().newJob()
                            .add("$daemon webui url")
                            .to(ArrayList<String>(), null)
                            .exec()
                        check(result.isSuccess) { failed }
                        result.out.firstOrNull { it.startsWith("http://127.0.0.1:9178/#") }
                            ?: error(failed)
                    } else null
                }
                if (url != null) uriHandler.openUri(url)
                message = done
            } catch (error: Exception) {
                message = error.message ?: failed
            } finally {
                busy = false
            }
        }
    }

    val state = IkunSettingsUiState(
        falling = effects.enabled,
        snow = effects.snow,
        particleScale = effects.scale,
        speed = effects.speed,
        hidden = hidden,
        recoveryCode = StealthController.DIAL_CODE,
        autostart = autostart,
        busy = busy,
        message = message,
    )
    val actions = IkunSettingsActions(
        onBack = { navigator.pop() },
        onSetFalling = effects::enable,
        onSetSnow = effects::snow,
        onSetParticleScale = effects::scale,
        onSetSpeed = effects::speed,
        onCopyCode = {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("🐾星棱狐🐾", StealthController.DIAL_CODE))
            message = copied
        },
        onOpenDialer = {
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL)) }
                .onFailure { message = dialerUnavailable }
        },
        onSetHidden = { value ->
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        if (value) {
                            check(withContext(Dispatchers.IO) { execKsud("stealth start") }) {
                                recoveryUnavailable
                            }
                        }
                        StealthController.setHidden(context, value)
                        hidden = StealthController.isHidden(context)
                        if (!value) withContext(Dispatchers.IO) { execKsud("stealth stop") }
                        message = done
                    } catch (error: Exception) {
                        message = error.message ?: failed
                    } finally {
                        busy = false
                    }
                }
            }
        },
        onOpenWebManager = { operation("webui start", open = true) },
        onStopWebManager = { operation("webui stop") },
        onSetAutostart = { value ->
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        val success = withContext(Dispatchers.IO) {
                            execKsud("webui autostart ${if (value) "on" else "off"}")
                        }
                        if (success) {
                            autostart = value
                            message = done
                        } else message = failed
                    } catch (error: Exception) {
                        message = error.message ?: failed
                    } finally {
                        busy = false
                    }
                }
            }
        },
    )

    // Navigation already supplies the active theme, including its colors and blur settings.
    when (LocalUiMode.current) {
        UiMode.Miuix -> IkunSettingsMiuix(state, actions)
        UiMode.Material -> IkunSettingsMaterial(state, actions)
    }
}
