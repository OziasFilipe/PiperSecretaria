package com.piper.secretaria.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.piper.secretaria.data.EventEntity
import com.piper.secretaria.data.SettingsStore
import com.piper.secretaria.data.TaskEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.piper.secretaria.util.Dates

/**
 * Agenda os avisos com AlarmManager em modo exato e permitido em Doze
 * (setExactAndAllowWhileIdle), que é o jeito mais confiável de tocar na hora certa.
 */
class ReminderScheduler(private val context: Context, private val settings: SettingsStore) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun syncTask(task: TaskEntity) {
        val at = task.remindAt
        if (task.done || at == null || at <= System.currentTimeMillis()) {
            cancelTask(task.id)
        } else {
            set(at, pending(ReminderReceiver.KIND_TASK, task.id))
        }
    }

    fun syncEvent(event: EventEntity) {
        val now = System.currentTimeMillis()
        val at = event.reminderAt()
        when {
            at == null || event.startAt <= now && (event.snoozedUntil ?: 0L) <= now -> cancelEvent(event.id)
            at > now -> set(at, pending(ReminderReceiver.KIND_EVENT, event.id))
            // O horário do aviso já passou, mas o compromisso ainda não: avisa já.
            else -> set(now + 1_500, pending(ReminderReceiver.KIND_EVENT, event.id))
        }
    }

    fun snooze(kind: String, id: Long, minutes: Int = 15): Long {
        val at = System.currentTimeMillis() + minutes * 60_000L
        set(at, pending(kind, id))
        return at
    }

    fun cancelTask(id: Long) = alarms.cancel(pending(ReminderReceiver.KIND_TASK, id))
    fun cancelEvent(id: Long) = alarms.cancel(pending(ReminderReceiver.KIND_EVENT, id))

    fun scheduleDailySummary() {
        val s = settings.current
        val pi = pending(ReminderReceiver.KIND_DAILY, 0)
        if (!s.dailySummaryEnabled) {
            alarms.cancel(pi); return
        }
        var next = LocalDateTime.of(LocalDate.now(), LocalTime.of(s.dailySummaryHour, s.dailySummaryMinute))
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        set(Dates.toMillis(next), pi)
    }

    private fun set(at: Long, pi: PendingIntent) {
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(kind: String, id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_KIND, kind)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, requestCode(kind, id), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        fun requestCode(kind: String, id: Long): Int = when (kind) {
            ReminderReceiver.KIND_EVENT -> (id * 3 + 1).toInt()
            ReminderReceiver.KIND_TASK -> (id * 3 + 2).toInt()
            else -> 0
        }
    }
}
