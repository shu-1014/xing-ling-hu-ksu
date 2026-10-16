package com.xinglinghu.manager.ui.stealth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.MainActivity

/**
 * Dialer escape hatch, shared by both disguises.
 *
 * Entering the configured code in the system dialer clears the hidden launcher
 * icon *and* turns off kernel stealth, then opens the Manager.
 *
 * A single code deliberately covers both: either mechanism left on its own can
 * make the app hard to get back into, and a user who has just restored the icon
 * should never be left staring at a UI that still claims nothing is installed.
 *
 * SECRET_CODE is not a protected broadcast, so any app could forge it. That is
 * acceptable here because this receiver only ever *restores* visibility — the
 * worst a forged broadcast achieves is un-hiding an app the user installed. The
 * code is a convenience, not a security boundary, which is why it is editable
 * without any re-authentication.
 */
class DialerRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!DialerRecoveryCode.matches(context, intent.action, intent.data?.toString())) return

        StealthController.setHidden(context, false)

        // Kernel stealth needs a round trip into the kernel, so it is done off the
        // broadcast thread. A kernel without the ioctl reports -1 and is left alone.
        val stealthRestored = runCatching {
            if (Stealth.state() == Stealth.State.ON) Stealth.setEnabled(false) else true
        }.getOrDefault(true)

        val message = if (stealthRestored) {
            R.string.ikun_icon_restored
        } else {
            R.string.ikun_recovery_unavailable
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        // Some ROMs block background activity launches; the restored icon remains usable.
        runCatching {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }
}
