package com.xinglinghu.manager.ui.screen.tools

import androidx.compose.runtime.Immutable

@Immutable
data class ToolsUiState(
    val selinuxEnforcing: Boolean = true,
    val selinuxLoading: Boolean = true,
    val selinuxStatusRaw: String = "",
)

@Immutable
data class ToolsActions(
    val onBack: () -> Unit,
    val onSelinuxToggle: (Boolean) -> Unit,
    val onBackupAllowlist: () -> Unit,
    val onRestoreAllowlist: () -> Unit,
)
