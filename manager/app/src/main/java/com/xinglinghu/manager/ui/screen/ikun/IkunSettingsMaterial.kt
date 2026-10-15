package com.xinglinghu.manager.ui.screen.ikun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.component.material.ExpressiveScaffold
import com.xinglinghu.manager.ui.component.material.ExpressiveSwitch
import com.xinglinghu.manager.ui.component.material.TonalCard
import com.xinglinghu.manager.ui.component.material.TopBarBackButton
import com.xinglinghu.manager.ui.component.material.expressiveTopAppBarColors
import kotlin.math.roundToInt

@Composable
internal fun IkunSettingsMaterial(state: IkunSettingsUiState, actions: IkunSettingsActions) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.ikun_settings_title)) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                TonalCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Toggle(stringResource(R.string.ikun_falling), state.falling, onCheckedChange = actions.onSetFalling)
                        Toggle(stringResource(R.string.ikun_snow), state.snow, onCheckedChange = actions.onSetSnow)
                        SliderOption(
                            title = stringResource(R.string.ikun_particle_size),
                            value = state.particleScale,
                            range = 0.5f..2f,
                            onValueChange = actions.onSetParticleScale,
                        )
                        SliderOption(
                            title = stringResource(R.string.ikun_particle_speed),
                            value = state.speed,
                            range = 0.3f..3f,
                            onValueChange = actions.onSetSpeed,
                        )
                    }
                }
            }
            item {
                TonalCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ikun_dialer_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer {
                            Text(state.recoveryCode, fontFamily = FontFamily.Monospace)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = actions.onCopyCode, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.ikun_copy_code))
                            }
                            Button(onClick = actions.onOpenDialer, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.ikun_open_dialer))
                            }
                        }
                        Toggle(stringResource(R.string.ikun_stealth), state.hidden, enabled = !state.busy, onCheckedChange = actions.onSetHidden)
                    }
                }
            }
            item {
                TonalCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ikun_web_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = actions.onOpenWebManager,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ikun_web_open))
                        }
                        FilledTonalButton(
                            onClick = actions.onStopWebManager,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ikun_web_stop))
                        }
                        Toggle(
                            title = stringResource(R.string.ikun_web_autostart),
                            checked = state.autostart,
                            enabled = !state.busy,
                            onCheckedChange = actions.onSetAutostart,
                        )
                    }
                }
            }
            if (state.message.isNotEmpty()) {
                item { Text(state.message, style = MaterialTheme.typography.bodyMedium) }
            }
            item {
                Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
        }
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        ExpressiveSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SliderOption(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val sliderState = rememberSliderState(value, trackRange = range)
    sliderState.value = value
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${(value * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            state = sliderState,
            onValueChange = onValueChange,
        )
    }
}
