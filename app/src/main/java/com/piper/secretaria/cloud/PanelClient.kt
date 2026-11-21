package com.piper.secretaria.cloud

import com.piper.secretaria.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PanelException(message: String, val unauthorized: Boolean = false) : Exception(message)

data class LoginResult(val token: String, val username: String, val name: String, val chatgpt: Boolean)

/** Conversa com o painel web (Vercel). Toda chamada é HTTPS e autenticada pela sessão do usuário. */
class PanelClient(private val baseUrlProvider: () -> String, private val tokenProvider: () -> String) {

    private fun base(): String = baseUrlProvider().trim().trimEnd('/')

    suspend fun login(baseUrl: String, username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("username", username).put("password", password)
            .put("device", "Android ${android.os.Build.MODEL}")
        val json = request("POST", normalizeUrl(baseUrl) + "/api/auth/login", body, token = null)
        val user = json.getJSONObject("user")
        LoginResult(json.getString("token"), user.getString("username"), user.optString("name"), json.optBoolean("chatgpt"))
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching { request("POST", base() + "/api/auth/logout", JSONObject(), tokenProvider()) }
    }

    suspend fun me(): JSONObject = withContext(Dispatchers.IO) { request("GET", base() + "/api/me", null, tokenProvider()) }

    /** Envia a conversa + contexto e recebe a resposta e as ações para executar. */
    suspend fun chat(messages: JSONArray, context: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        request("POST", base() + "/api/chat", JSONObject().put("messages", messages).put("context", context), tokenProvider(), readTimeout = 45_000)
    }

    suspend fun getMemory(): JSONArray = withContext(Dispatchers.IO) {
        request("GET", base() + "/api/memory", null, tokenProvider()).optJSONArray("memories") ?: JSONArray()
    }

    suspend fun putMemory(memories: JSONArray) = withContext(Dispatchers.IO) {
        request("PUT", base() + "/api/memory", JSONObject().put("memories", memories), tokenProvider())
    }

    /** Ações combinadas pelo painel web, aguardando para serem aplicadas no celular. */
    suspend fun pullInbox(): JSONArray = withContext(Dispatchers.IO) {
        request("POST", base() + "/api/inbox", JSONObject(), tokenProvider()).optJSONArray("actions") ?: JSONArray()
    }

    private fun request(method: String, url: String, body: JSONObject?, token: String?, readTimeout: Int = 20_000): JSONObject {
        if (!url.startsWith("https://")) throw PanelException("O endereço do painel precisa começar com https://")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            this.readTimeout = readTimeout
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Piper-Android/${BuildConfig.VERSION_NAME}")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray()) }
            }
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                throw PanelException(json.optString("error").ifBlank { "Erro $code no painel." }, unauthorized = code == 401)
            }
            return json
        } catch (e: PanelException) {
            throw e
        } catch (e: Exception) {
            throw PanelException("Sem conexão com o painel.")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun normalizeUrl(raw: String): String {
            var u = raw.trim().trimEnd('/')
            if (u.isNotBlank() && !u.startsWith("http")) u = "https://$u"
            return u
        }
    }
}
