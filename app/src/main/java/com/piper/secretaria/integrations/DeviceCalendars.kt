package com.piper.secretaria.integrations

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.piper.secretaria.data.EventEntity
import com.piper.secretaria.data.EventType
import com.piper.secretaria.data.PiperRepository
import com.piper.secretaria.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceCalendar(val id: Long, val name: String, val account: String, val provider: String, val color: Int)

/**
 * Integração com as agendas que já estão no celular. Qualquer conta que o Android sincroniza
 * (Outlook/Exchange pelo app Outlook, Google Agenda, Samsung, iCloud via app…) aparece aqui,
 * sem precisar de chave de API. A Piper importa os compromissos e passa a avisar deles.
 */
class DeviceCalendars(
    private val context: Context,
    private val repo: PiperRepository,
    private val settings: SettingsStore,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun list(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val out = mutableListOf<DeviceCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += DeviceCalendar(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Agenda",
                    account = c.getString(2) ?: "",
                    provider = providerName(c.getString(3) ?: ""),
                    color = c.getInt(4),
                )
            }
        }
        out.sortedWith(compareBy({ it.provider }, { it.account }, { it.name }))
    }

    /** Importa de 1 dia atrás até 60 dias à frente. Retorna quantos compromissos estão sincronizados. */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val s = settings.current
        val existing = repo.externalEvents().associateBy { it.externalId }
        if (!s.deviceCalendarsEnabled || !hasPermission() || s.deviceCalendarIds.isEmpty()) {
            existing.values.forEach { repo.deleteEvent(it) }
            return@withContext 0
        }
        val calendars = list().associateBy { it.id }
        val now = System.currentTimeMillis()
        val from = now - 86_400_000L
        val to = now + 60 * 86_400_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val ids = s.deviceCalendarIds.joinToString(",")
        val seen = mutableSetOf<String>()
        context.contentResolver.query(
            uri, projection, "${CalendarContract.Instances.CALENDAR_ID} IN ($ids)", null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val begin = c.getLong(1)
                val end = c.getLong(2)
                val allDay = c.getInt(6) == 1
                val externalId = "cal:${c.getLong(0)}:$begin"
                seen += externalId
                val title = c.getString(3)?.takeIf { it.isNotBlank() } ?: "(sem título)"
                val location = c.getString(7).orEmpty()
                val description = listOf(location, c.getString(4).orEmpty().take(500)).filter { it.isNotBlank() }.joinToString("\n")
                val cal = calendars[c.getLong(5)]
                val source = cal?.let { "${it.provider} · ${it.account.ifBlank { it.name }}" } ?: "Agenda do celular"
                val old = existing[externalId]
                val start = if (allDay) begin + 9 * 3_600_000L - java.util.TimeZone.getDefault().getOffset(begin) else begin
                val event = (old ?: EventEntity(title = title, startAt = start, reminderMinutes = s.defaultReminderMinutes)).copy(
                    title = title,
                    description = description,
                    startAt = start,
                    durationMinutes = ((end - begin) / 60_000L).toInt().coerceIn(15, 24 * 60),
                    type = guessType(title, location),
                    externalId = externalId,
                    source = source,
                    reminderMinutes = if (allDay) -1 else (old?.reminderMinutes ?: s.defaultReminderMinutes),
                )
                if (old == null || old != event) repo.saveEvent(event)
            }
        }
        // Remove o que foi apagado na agenda de origem
        existing.filterKeys { it !in seen }.values.forEach { repo.deleteEvent(it) }
        settings.update { it.copy(lastCalendarSync = System.currentTimeMillis()) }
        seen.size
    }

    private fun guessType(title: String, location: String): EventType {
        val t = (title + " " + location).lowercase()
        return when {
            listOf("teams", "meet", "zoom", "reuni", "call", "online").any { it in t } -> EventType.MEETING
            listOf("ligar", "ligação", "telefon").any { it in t } -> EventType.CALL
            listOf("aniver", "médico", "medico", "academia", "pessoal").any { it in t } -> EventType.PERSONAL
            else -> EventType.TASK
        }
    }

    private fun providerName(accountType: String): String = when {
        accountType.contains("outlook", true) || accountType.contains("exchange", true) ||
            accountType.contains("microsoft", true) || accountType.contains("office", true) -> "Outlook"
        accountType == "com.google" -> "Google"
        accountType.contains("samsung", true) || accountType.contains("osp", true) -> "Samsung"
        accountType.contains("icloud", true) || accountType.contains("apple", true) -> "iCloud"
        accountType == CalendarContract.ACCOUNT_TYPE_LOCAL || accountType.contains("local", true) -> "Celular"
        else -> accountType.substringAfterLast('.').replaceFirstChar { it.uppercase() }.ifBlank { "Agenda" }
    }
}
