package com.xinglinghu.manager.ui.screen.about

import androidx.compose.runtime.Immutable

@Immutable
data class AboutUiState(
    val title: String,
    val appName: String,
    val versionName: String,
    /**
     * Contact rows rendered on the About page. These are not links: tapping a
     * row copies its value to the clipboard instead of leaving the app.
     */
    val contacts: List<ContactInfo>,
)

@Immutable
data class ContactInfo(
    val title: String,
    val value: String,
    val summary: String,
)

@Immutable
data class AboutScreenActions(
    val onBack: () -> Unit,
    val onCopyContact: (String) -> Unit,
)
