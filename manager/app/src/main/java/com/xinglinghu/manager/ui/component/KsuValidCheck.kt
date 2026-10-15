package com.xinglinghu.manager.ui.component

import androidx.compose.runtime.Composable
import com.xinglinghu.manager.Natives

@Composable
fun KsuIsValid(
    content: @Composable () -> Unit
) {
    val isManager = Natives.isManager
    val ksuVersion = if (isManager) Natives.version else null

    if (ksuVersion != null) {
        content()
    }
}
