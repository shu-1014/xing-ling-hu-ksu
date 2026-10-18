package com.xinglinghu.manager.ui.screen.tools

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.component.KsuIsValid
import com.xinglinghu.manager.ui.component.material.ExpressiveScaffold
import com.xinglinghu.manager.ui.component.material.SegmentedColumn
import com.xinglinghu.manager.ui.component.material.SegmentedListItem
import com.xinglinghu.manager.ui.component.material.SegmentedSwitchItem
import com.xinglinghu.manager.ui.component.material.TopBarBackButton
import com.xinglinghu.manager.ui.component.material.expressiveTopAppBarColors

@Composable
fun ToolsMaterial(
    state: ToolsUiState,
    actions: ToolsActions,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_tools)) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                scrollBehavior = scrollBehavior,
                colors = expressiveTopAppBarColors(),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
            contentPadding = innerPadding,
        ) {
            item {
                KsuIsValid {
                    SegmentedColumn(
                        modifier = Modifier.padding(top = 12.dp),
                        content = listOf({
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Security,
                                title = stringResource(R.string.tools_selinux_toggle),
                                summary = stringResource(
                                    R.string.tools_selinux_summary,
                                    selinuxStatusLabel(state.selinuxStatusRaw)
                                ),
                                checked = state.selinuxEnforcing,
                                enabled = !state.selinuxLoading,
                                onCheckedChange = actions.onSelinuxToggle,
                            )
                        })
                    )

                    AllowlistBackupSectionMaterial(
                        onBackup = actions.onBackupAllowlist,
                        onRestore = actions.onRestoreAllowlist,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllowlistBackupSectionMaterial(
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.padding(vertical = 12.dp),
        content = listOf(
            {
                val backupTitle = stringResource(R.string.allowlist_backup_title)
                SegmentedListItem(
                    headlineContent = { Text(backupTitle) },
                    supportingContent = { Text(stringResource(R.string.allowlist_backup_summary_picker)) },
                    leadingContent = { Icon(Icons.Filled.Backup, backupTitle) },
                    onClick = onBackup,
                )
            },
            {
                val restoreTitle = stringResource(R.string.allowlist_restore_title)
                SegmentedListItem(
                    headlineContent = { Text(restoreTitle) },
                    supportingContent = { Text(stringResource(R.string.allowlist_restore_summary_picker)) },
                    leadingContent = { Icon(Icons.Filled.Restore, restoreTitle) },
                    onClick = onRestore,
                )
            },
        )
    )
}
