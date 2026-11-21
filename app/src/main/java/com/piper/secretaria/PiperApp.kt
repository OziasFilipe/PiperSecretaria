package com.piper.secretaria

import android.app.Application
import com.piper.secretaria.data.PiperDatabase
import com.piper.secretaria.data.PiperRepository
import com.piper.secretaria.data.SettingsStore
import com.piper.secretaria.reminders.Notifier
import com.piper.secretaria.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PiperApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings by lazy { SettingsStore(this) }
    val database by lazy { PiperDatabase.create(this) }
    val scheduler by lazy { ReminderScheduler(this, settings) }
    val repository by lazy { PiperRepository(database, scheduler) }
    val notifier by lazy { Notifier(this) }

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        appScope.launch { repository.rescheduleAll() }
    }
}

val android.content.Context.piper: PiperApp get() = applicationContext as PiperApp
