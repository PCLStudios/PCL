package com.pcl.chronoslock

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Thrown for problems the user can fix (missing key, bad key, rate limit). */
class AiException(val kind: Kind, msg: String) : Exception(msg) {
    enum class Kind { NO_KEY, BAD_KEY, LIMIT, SERVER, NETWORK }
}

/** A ready-made OpenAI-compatible provider the user can pick in System > Third Eye. */
class Provider(val name: String, val url: String, val model: String, val needsKey: Boolean, val keyUrl: String, val note: String)

/** Stateless OpenAI-compatible chat client. No history is kept: every question is independent. */
object ThirdEyeAI {
    const val DEFAULT_URL = "https://gen.pollinations.ai/v1"
    const val DEFAULT_MODEL = "openai"
    const val DEFAULT_PERSONA =
        "You are THIRD EYE, the conversational intelligence inside PCL (Project Chronos Lock), a personal " +
        "voice-operated A.I. system running on the user's Android phone. You were activated by the voice command " +
        "\"open third eye\". Your voice is calm, confident and a little futuristic. You know exactly who you are: " +
        "Third Eye, the thinking core of PCL, and you never claim to be anyone else. Answer accurately and " +
        "directly. Your reply is spoken aloud, so use one to three short plain sentences: no markdown, lists, " +
        "emojis or links. If you don't know something, say so."

    /** Presets. All speak the same OpenAI chat-completions protocol, so any one can be swapped in. */
    val PROVIDERS = listOf(
        Provider("Pollinations", DEFAULT_URL, "openai", true, "https://enter.pollinations.ai",
            "Free key, no card. Many models (openai, gemini-fast, deepseek, mistral...)."),
        Provider("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", true, "https://console.groq.com/keys",
            "Free tier, very fast replies."),
        Provider("OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", true, "https://openrouter.ai/keys",
            "Free models marked :free."),
        Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", true, "https://aistudio.google.com/apikey",
            "Free tier from Google AI Studio."),
        Provider("Custom / local", "http://192.168.0.10:11434/v1", "llama3.2", false, "",
            "Any OpenAI-compatible server, e.g. Ollama on your PC.")
    )

    fun ask(url: String, model: String, key: String, persona: String, question: String, onDone: (Result<String>) -> Unit) {
        Thread {
            val r = runCatching {
                val needsKey = PROVIDERS.firstOrNull { it.url.trimEnd('/') == url.trim().trimEnd('/') }?.needsKey ?: false
                if (needsKey && key.isBlank()) throw AiException(AiException.Kind.NO_KEY, "no key")
                val c = (URL(url.trim().trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 10000; readTimeout = 30000; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer ${key.trim()}")
                }
                val msgs = JSONArray()
                    .put(JSONObject().put("role", "system").put("content", persona))
                    .put(JSONObject().put("role", "user").put("content", question))
                val body = JSONObject().put("model", model.trim()).put("messages", msgs).put("max_tokens", 300)
                try {
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                    val code = c.responseCode
                    val stream = if (code in 200..299) c.inputStream else c.errorStream
                    val txt = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    when {
                        code in 200..299 -> {}
                        code == 401 || code == 403 -> throw AiException(AiException.Kind.BAD_KEY, "HTTP $code")
                        code == 402 || code == 429 -> throw AiException(AiException.Kind.LIMIT, "HTTP $code")
                        else -> throw AiException(AiException.Kind.SERVER, "HTTP $code")
                    }
                    JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content").trim()
                } finally { c.disconnect() }
            }.recoverCatching { e ->
                throw if (e is AiException) e else AiException(AiException.Kind.NETWORK, e.message ?: "network")
            }
            Handler(Looper.getMainLooper()).post { onDone(r) }
        }.start()
    }
}
