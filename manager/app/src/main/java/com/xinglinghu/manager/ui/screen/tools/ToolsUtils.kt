package com.xinglinghu.manager.ui.screen.tools

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.util.getRootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val ALLOWLIST_PATH = "/data/adb/ksu/.allowlist"

/**
 * Maps a raw getenforce-style status ("Enforcing", "Permissive", "Disabled", or anything else)
 * onto the localized status strings already used by the home screen cards.
 */
@Composable
fun selinuxStatusLabel(raw: String): String = when (raw) {
    "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
    "Permissive" -> stringResource(R.string.selinux_status_permissive)
    "Disabled" -> stringResource(R.string.selinux_status_disabled)
    else -> stringResource(R.string.selinux_status_unknown)
}

/**
 * Switches SELinux between permissive and enforcing. [permissive] true sets permissive (0).
 * Returns true when the underlying shell command succeeded.
 * Safe to call from any thread (IO recommended).
 */
fun setSelinuxPermissive(permissive: Boolean): Boolean {
    val target = if (permissive) "0" else "1"
    val result = getRootShell().newJob().add("setenforce $target").exec()
    return result.isSuccess
}

/**
 * Copies the kernel allowlist into the file behind [targetUri].
 * Returns true on success. Safe to call from any thread (IO recommended).
 */
suspend fun backupAllowlistToUri(context: Context, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
    val tempFile = File(context.cacheDir, "allowlist_backup_tmp.bin")
    try {
        if (!copyAllowlistToFile(tempFile)) return@withContext false
        return@withContext runCatching {
            context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
                true
            } ?: false
        }.getOrElse { false }
    } finally {
        tempFile.delete()
    }
}

/**
 * Restores the kernel allowlist from the file behind [sourceUri].
 * Returns true on success. Safe to call from any thread (IO recommended).
 */
suspend fun restoreAllowlistFromUri(context: Context, sourceUri: Uri): Boolean = withContext(Dispatchers.IO) {
    val tempFile = File(context.cacheDir, "allowlist_restore_tmp.bin")
    try {
        val downloaded = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false
        }.getOrElse { false }
        if (!downloaded) return@withContext false
        return@withContext copyFileToAllowlist(tempFile)
    } finally {
        tempFile.delete()
    }
}

private fun copyAllowlistToFile(targetFile: File): Boolean {
    return runCatching {
        targetFile.parentFile?.mkdirs()
        getRootShell().newJob()
            .add("cp $ALLOWLIST_PATH \"${targetFile.absolutePath}\"")
            .add("chmod 0644 \"${targetFile.absolutePath}\"")
            .exec()
            .isSuccess
    }.getOrDefault(false)
}

private fun copyFileToAllowlist(sourceFile: File): Boolean {
    if (!sourceFile.exists()) return false
    return runCatching {
        getRootShell().newJob()
            .add("cp \"${sourceFile.absolutePath}\" $ALLOWLIST_PATH")
            .add("chmod 0644 $ALLOWLIST_PATH")
            .exec()
            .isSuccess
    }.getOrDefault(false)
}
