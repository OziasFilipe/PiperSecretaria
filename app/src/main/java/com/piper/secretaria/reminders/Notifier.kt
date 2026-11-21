package com.piper.secretaria.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.piper.secretaria.MainActivity
import com.piper.secretaria.R

class Notifier(private val context: Context) {

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Lembretes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos de compromissos e tarefas"
                enableVibration(true)
                setShowBadge(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SUMMARY, "Resumo do dia", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Resumo diário da sua pauta"
            },
        )
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun eventReminder(notificationId: Int, eventId: Long, headline: String, title: String) {
        val builder = base(CH_REMINDERS, headline, "$title\nDeseja ver a pauta agora?")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(notificationId, "agenda", eventId))
            .addAction(0, "Ver pauta", openApp(notificationId + 100_000, "agenda", eventId))
            .addAction(0, "Adiar 15 min", action(ReminderReceiver.ACTION_SNOOZE, ReminderReceiver.KIND_EVENT, eventId, notificationId))
        post(notificationId, builder)
    }

    fun taskReminder(notificationId: Int, taskId: Long, title: String, notes: String) {
        val text = if (notes.isBlank()) title else "$title\n$notes"
        val builder = base(CH_REMINDERS, "Lembrete de tarefa", text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(notificationId, "tasks", taskId))
            .addAction(0, "Concluir", action(ReminderReceiver.ACTION_DONE, ReminderReceiver.KIND_TASK, taskId, notificationId))
            .addAction(0, "Adiar 15 min", action(ReminderReceiver.ACTION_SNOOZE, ReminderReceiver.KIND_TASK, taskId, notificationId))
        post(notificationId, builder)
    }

    fun summary(title: String, text: String) {
        val builder = base(CH_SUMMARY, title, text)
            .setContentIntent(openApp(SUMMARY_ID, "home", 0))
        post(SUMMARY_ID, builder)
    }

    fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun base(channel: String, title: String, text: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF7C3AED.toInt())
            .setSubText("Piper Assistente")
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        if (!canNotify()) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun openApp(requestCode: Int, route: String, id: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .putExtra(MainActivity.EXTRA_ITEM_ID, id)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(action: String, kind: String, id: Long, notificationId: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderReceiver.EXTRA_KIND, kind)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
            .putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        val code = notificationId + if (action == ReminderReceiver.ACTION_SNOOZE) 200_000 else 300_000
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CH_REMINDERS = "lembretes"
        const val CH_SUMMARY = "resumo"
        const val SUMMARY_ID = 999_999
    }
}
