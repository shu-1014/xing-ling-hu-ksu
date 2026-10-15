package com.xinglinghu.manager.ui.stealth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.MainActivity

class DialerRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!DialerRecoveryCode.matches(intent.action, intent.data?.toString())) return
        StealthController.setHidden(context, false)
        Toast.makeText(context, R.string.ikun_icon_restored, Toast.LENGTH_SHORT).show()
        // Some ROMs block background activity launches; the restored icon remains usable.
        runCatching {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }
}
