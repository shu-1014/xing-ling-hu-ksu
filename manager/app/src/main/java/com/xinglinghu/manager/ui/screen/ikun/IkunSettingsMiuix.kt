package com.xinglinghu.manager.ui.screen.ikun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.cosmetic.FallingEffect
import com.xinglinghu.manager.ui.theme.LocalEnableBlur
import com.xinglinghu.manager.ui.util.BlurredBar
import com.xinglinghu.manager.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

@Composable
internal fun IkunSettingsMiuix(state: IkunSettingsUiState, actions: IkunSettingsActions) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    val listState = rememberLazyListState()
    // The result text lives at the end of the list, so an action taken near the
    // bottom would otherwise report its outcome off-screen and look like nothing
    // happened at all. Bring the message into view whenever it changes.
    LaunchedEffect(state.message) {
        if (state.message.isNotEmpty() && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }
    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.ikun_settings_title),
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            val direction = LocalLayoutDirection.current
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.graphicsLayer {
                                    if (direction == LayoutDirection.Rtl) scaleX = -1f
                                },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        SwitchPreference(
                            title = stringResource(R.string.ikun_falling),
                            checked = state.falling,
                            onCheckedChange = actions.onSetFalling,
                        )
                        BasicComponent(
                            title = stringResource(R.string.ikun_effect),
                            endActions = {
                                Text(state.effect.label(), color = colorScheme.onSurfaceVariantActions)
                            },
                            bottomAction = {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FallingEffect.entries.forEach { effect ->
                                        TextButton(
                                            text = effect.label(),
                                            onClick = { actions.onSetEffect(effect) },
                                            modifier = Modifier.weight(1f),
                                            colors = if (state.effect == effect) {
                                                ButtonDefaults.textButtonColorsPrimary()
                                            } else {
                                                ButtonDefaults.textButtonColors()
                                            },
                                        )
                                    }
                                }
                            },
                        )
                        BasicComponent(
                            title = stringResource(R.string.ikun_particle_size),
                            endActions = {
                                Text("${(state.particleScale * 100).roundToInt()}%", color = colorScheme.onSurfaceVariantActions)
                            },
                            bottomAction = {
                                Slider(
                                    value = state.particleScale,
                                    onValueChange = actions.onSetParticleScale,
                                    valueRange = 0.5f..2f,
                                )
                            },
                        )
                        BasicComponent(
                            title = stringResource(R.string.ikun_particle_speed),
                            endActions = {
                                Text("${(state.speed * 100).roundToInt()}%", color = colorScheme.onSurfaceVariantActions)
                            },
                            bottomAction = {
                                Slider(
                                    value = state.speed,
                                    onValueChange = actions.onSetSpeed,
                                    valueRange = 0.3f..3f,
                                )
                            },
                        )
                    }
                }
                item {
                    Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        SwitchPreference(
                            title = stringResource(R.string.ikun_tilt),
                            summary = stringResource(R.string.ikun_tilt_summary),
                            checked = state.tilt,
                            enabled = state.effect == FallingEffect.SNOW || state.effect == FallingEffect.TROLL,
                            onCheckedChange = actions.onSetTilt,
                        )
                        BasicComponent(
                            title = stringResource(R.string.ikun_gravity_mode),
                            summary = stringResource(R.string.ikun_gravity_summary),
                            endActions = {
                                Text(
                                    stringResource(GRAVITY_KEYS[state.gravityMode]),
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            bottomAction = {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    GRAVITY_KEYS.forEachIndexed { index, key ->
                                        TextButton(
                                            text = stringResource(key),
                                            onClick = { actions.onSetGravityMode(index) },
                                            modifier = Modifier.weight(1f),
                                            colors = if (state.gravityMode == index) {
                                                ButtonDefaults.textButtonColorsPrimary()
                                            } else {
                                                ButtonDefaults.textButtonColors()
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                item {
                    Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        BasicComponent(
                            title = stringResource(R.string.ikun_wallpaper),
                            summary = stringResource(R.string.ikun_wallpaper_summary),
                        )
                        BasicComponent(
                            title = stringResource(R.string.ikun_wallpaper_alpha),
                            endActions = {
                                Text("${(state.wallpaperAlpha * 100).roundToInt()}%", color = colorScheme.onSurfaceVariantActions)
                            },
                            bottomAction = {
                                Slider(
                                    value = state.wallpaperAlpha,
                                    onValueChange = actions.onSetWallpaperAlpha,
                                    valueRange = 0f..1f,
                                )
                            },
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextButton(
                                text = stringResource(R.string.ikun_wallpaper_pick),
                                onClick = actions.onPickWallpaper,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                            TextButton(
                                text = stringResource(R.string.ikun_wallpaper_reset),
                                onClick = actions.onResetWallpaper,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        SwitchPreference(
                            title = stringResource(R.string.ikun_announcement),
                            summary = stringResource(R.string.ikun_announcement_summary),
                            checked = state.announcementEnabled,
                            onCheckedChange = actions.onSetAnnouncement,
                        )
                    }
                }
                item {
                    Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.ikun_dialer_description), color = colorScheme.onSurfaceVariantSummary)
                            SelectionContainer {
                                Text(state.recoveryCode, fontFamily = FontFamily.Monospace)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(
                                    text = stringResource(R.string.ikun_copy_code),
                                    onClick = actions.onCopyCode,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    text = stringResource(R.string.ikun_open_dialer),
                                    onClick = actions.onOpenDialer,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                            }
                        }
                        SwitchPreference(
                            title = stringResource(R.string.ikun_stealth),
                            summary = stringResource(R.string.ikun_stealth_summary),
                            checked = state.hidden,
                            enabled = !state.busy,
                            onCheckedChange = actions.onSetHidden,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.ikun_kernel_stealth),
                            summary = stringResource(R.string.ikun_kernel_stealth_summary),
                            checked = state.kernelStealth,
                            enabled = !state.busy,
                            onCheckedChange = actions.onSetKernelStealth,
                        )
                    }
                }
                item {
                    Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.ikun_web_summary), color = colorScheme.onSurfaceVariantSummary)
                            TextButton(
                                text = stringResource(R.string.ikun_web_open),
                                onClick = actions.onOpenWebManager,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                            TextButton(
                                text = stringResource(R.string.ikun_web_stop),
                                onClick = actions.onStopWebManager,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        SwitchPreference(
                            title = stringResource(R.string.ikun_web_autostart),
                            checked = state.autostart,
                            enabled = !state.busy,
                            onCheckedChange = actions.onSetAutostart,
                        )
                    }
                }
                if (state.message.isNotEmpty()) {
                    item {
                        Text(state.message, Modifier.padding(horizontal = 4.dp, vertical = 12.dp))
                    }
                }
                item {
                    Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp))
                }
            }
        }
    }
}
