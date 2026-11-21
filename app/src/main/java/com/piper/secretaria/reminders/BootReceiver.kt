package com.piper.secretaria.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piper.secretaria.piper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarmes somem quando o celular reinicia: aqui eles são recriados. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.piper.repository.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
