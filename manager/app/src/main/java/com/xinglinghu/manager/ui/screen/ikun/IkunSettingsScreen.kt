package com.xinglinghu.manager.ui.screen.ikun

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.xinglinghu.manager.ui.cosmetic.AnnouncementPrefs
import com.xinglinghu.manager.ui.cosmetic.FallingEffect
import com.xinglinghu.manager.ui.cosmetic.LocalCosmeticState
import com.xinglinghu.manager.ui.cosmetic.WallpaperSlot
import com.xinglinghu.manager.ui.cosmetic.WallpaperStore
import com.xinglinghu.manager.ui.navigation3.LocalNavigator
import com.xinglinghu.manager.ui.stealth.Stealth
import com.xinglinghu.manager.ui.stealth.StealthController
import com.xinglinghu.manager.ui.util.execKsud
import com.xinglinghu.manager.ui.util.getRootShell

internal data class IkunSettingsUiState(
    val falling: Boolean,
    val effect: FallingEffect,
    val particleScale: Float,
    val speed: Float,
    val tilt: Boolean,
    val gravityMode: Int,
    val wallpaperAlpha: Float,
    val announcementEnabled: Boolean,
    val hidden: Boolean,
    val kernelStealth: Boolean,
    val recoveryCode: String,
    val autostart: Boolean,
    val busy: Boolean,
    val message: String,
)

internal data class IkunSettingsActions(
    val onBack: () -> Unit,
    val onSetFalling: (Boolean) -> Unit,
    val onSetEffect: (FallingEffect) -> Unit,
    val onSetParticleScale: (Float) -> Unit,
    val onSetSpeed: (Float) -> Unit,
    val onSetTilt: (Boolean) -> Unit,
    val onSetGravityMode: (Int) -> Unit,
    val onSetWallpaperAlpha: (Float) -> Unit,
    val onPickWallpaper: () -> Unit,
    val onResetWallpaper: () -> Unit,
    val onSetAnnouncement: (Boolean) -> Unit,
    val onCopyCode: () -> Unit,
    val onOpenDialer: () -> Unit,
    val onSetHidden: (Boolean) -> Unit,
    val onSetKernelStealth: (Boolean) -> Unit,
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
    // Only ever shows the switch as on when the kernel explicitly said so; an
    // unreadable value must not be presented as "off", or the user would believe
    // root UI is visible when it may not be.
    var kernelStealth by remember { mutableStateOf(Stealth.isEnabled()) }
    var autostart by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val failed = stringResource(R.string.ikun_operation_failed)
    val done = stringResource(R.string.ikun_operation_done)
    val copied = stringResource(R.string.ikun_code_copied)
    val dialerUnavailable = stringResource(R.string.ikun_dialer_unavailable)
    val recoveryUnavailable = stringResource(R.string.ikun_recovery_unavailable)
    val wallpaperPicked = stringResource(R.string.ikun_wallpaper_picked)
    val wallpaperFailed = stringResource(R.string.ikun_wallpaper_failed)
    val wallpaperCleared = stringResource(R.string.ikun_wallpaper_cleared)

    // The wallpaper is copied into private storage as soon as the picker returns,
    // so the returned content URI is never needed again and no persistable
    // permission has to be requested.
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val slot = WallpaperStore.slotFor(context.resources.configuration)
            val ok = withContext(Dispatchers.IO) { WallpaperStore.pick(slot, uri) }
            message = if (ok) wallpaperPicked else wallpaperFailed
        }
    }

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
        effect = effects.effect,
        particleScale = effects.scale,
        speed = effects.speed,
        tilt = effects.tilt,
        gravityMode = effects.gravityMode,
        wallpaperAlpha = effects.wallpaperAlpha,
        announcementEnabled = effects.announcementEnabled,
        hidden = hidden,
        kernelStealth = kernelStealth,
        recoveryCode = StealthController.DIAL_CODE,
        autostart = autostart,
        busy = busy,
        message = message,
    )
    val actions = IkunSettingsActions(
        onBack = { navigator.pop() },
        onSetFalling = effects::enable,
        onSetEffect = { value ->
            effects.effect(value)
            // The master switch mirrors the picker so choosing an effect always
            // makes it visible; picking "off" turns the overlay off.
            effects.enable(value != FallingEffect.OFF)
        },
        onSetParticleScale = effects::scale,
        onSetSpeed = effects::speed,
        onSetTilt = effects::tilt,
        onSetGravityMode = effects::gravityMode,
        onSetWallpaperAlpha = effects::wallpaperAlpha,
        onPickWallpaper = {
            wallpaperPicker.launch("image/*")
        },
        onResetWallpaper = {
            WallpaperSlot.entries.forEach(WallpaperStore::clear)
            WallpaperStore.seedDefaultsIfNeeded()
            message = wallpaperCleared
        },
        onSetAnnouncement = { value ->
            // CosmeticState writes the shared "never show" key that
            // AnnouncementPrefs reads, so no second write is needed here.
            effects.announcementEnabled(value)
        },
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
        onSetKernelStealth = { value ->
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        // The kernel write is a real ioctl, so keep it off the main
                        // thread and surface the specific reason when it fails.
                        val error = withContext(Dispatchers.IO) { Stealth.setEnabledReporting(value) }
                        if (error != null) {
                            message = error
                        } else {
                            kernelStealth = Stealth.isEnabled()
                            message = done
                        }
                    } catch (e: Exception) {
                        message = e.message ?: failed
                    } finally {
                        busy = false
                    }
                }
            }
        },
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
