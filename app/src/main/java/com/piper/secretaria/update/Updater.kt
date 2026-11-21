package com.piper.secretaria.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.piper.secretaria.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Conteúdo do arquivo update.json publicado junto com cada versão:
 * { "versionCode": 3, "versionName": "1.2", "apkUrl": "https://.../Piper.apk", "notes": "O que mudou" }
 */
data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/** Verifica se há versão nova, baixa o APK e abre o instalador do Android. */
class Updater(private val context: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Checagem silenciosa feita toda vez que o app é aberto (sem mensagem de erro se estiver sem internet). */
    suspend fun checkOnOpen(): UpdateState = when (val s = _state.value) {
        is UpdateState.Downloading, is UpdateState.ReadyToInstall -> s
        else -> check(silent = true)
    }

    suspend fun check(silent: Boolean = false): UpdateState {
        if (_state.value is UpdateState.Downloading) return _state.value
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) {
            try {
                val json = JSONObject(httpGetText(BuildConfig.UPDATE_URL))
                val info = UpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.optString("versionName", "?"),
                    apkUrl = json.getString("apkUrl"),
                    notes = json.optString("notes", ""),
                )
                if (info.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                if (silent) UpdateState.Idle
                else UpdateState.Error("Não consegui verificar agora. Confira a internet e tente de novo.")
            }
        }
        _state.value = result
        return result
    }

    suspend fun download(info: UpdateInfo) {
        _state.value = UpdateState.Downloading(info, 0f)
        _state.value = withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "Piper-${info.versionName}.apk")
                val conn = open(info.apkUrl)
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) _state.value = UpdateState.Downloading(info, read.toFloat() / total)
                        }
                    }
                }
                UpdateState.ReadyToInstall(info, file)
            } catch (e: Exception) {
                UpdateState.Error("O download falhou. Tente de novo em instantes.")
            }
        }
    }

    /** Android 8+ pede que o usuário autorize a Piper a instalar apps (uma vez só). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    private fun httpGetText(url: String): String = open(url).inputStream.bufferedReader().use { it.readText() }

    /** Abre a conexão seguindo redirecionamentos (o GitHub redireciona os downloads). */
    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Piper-Android/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                current = URL(URL(current), conn.getHeaderField("Location")).toString()
                conn.disconnect()
            } else if (code in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw IllegalStateException("HTTP $code")
            }
        }
        throw IllegalStateException("Redirecionamentos demais")
    }
}
