package com.pcl.chronoslock

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Thrown for problems the user can fix (missing key, bad key, rate limit...). [detail] is the real technical reason. */
class AiException(val kind: Kind, val detail: String) : Exception(detail) {
    enum class Kind { NO_KEY, BAD_KEY, LIMIT, SERVER, NETWORK, BAD_REPLY, BAD_URL }
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

    /** Persona used when the user hasn't written their own. It always uses the chatbot's current name. */
    fun defaultPersona(name: String) =
        "You are $name, the conversational intelligence inside PCL (Project Chronos Lock), a personal " +
        "voice-operated A.I. system running on the user's Android phone. You were activated by the voice command " +
        "\"open $name\". Your voice is calm, confident and a little futuristic. You know exactly who you are: " +
        "$name, the thinking core of PCL, and you never claim to be anyone else. Answer accurately and " +
        "directly. Your reply is spoken aloud, so use one to three short plain sentences: no markdown, lists, " +
        "emojis or links. If you don't know something, say so."

    /** What is actually sent as the system prompt: the persona plus a reminder of the name. */
    fun buildPersona(name: String, custom: String): String =
        (if (custom.isBlank()) defaultPersona(name) else custom.trim()) +
        " Your name is $name. If the user calls you $name, that is you; always answer to it."

    private const val UA = "Mozilla/5.0 (Linux; Android 13) PCL/0.5.0"

    /** Presets. All speak the same OpenAI chat-completions protocol, so any one can be swapped in. */
    val PROVIDERS = listOf(
        Provider("Pollinations", DEFAULT_URL, "openai", true, "https://enter.pollinations.ai",
            "Free key, no card. Many models (openai, gemini-fast, deepseek, mistral...)."),
        Provider("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", true, "https://console.groq.com/keys",
            "Free tier, very fast replies. Most reliable choice."),
        Provider("OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", true, "https://openrouter.ai/keys",
            "Free models marked :free."),
        Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", true, "https://aistudio.google.com/apikey",
            "Free tier from Google AI Studio."),
        Provider("Custom / local", "http://192.168.0.10:11434/v1", "llama3.2", false, "",
            "Any OpenAI-compatible server, e.g. Ollama on your PC (plain http:// is allowed).")
    )

    /** Tidy what the user typed: add https://, strip spaces, and drop a pasted "/chat/completions". */
    fun cleanUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.isEmpty()) return u
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "https://$u"
        if (u.endsWith("/chat/completions")) u = u.removeSuffix("/chat/completions").trimEnd('/')
        return u
    }

    fun providerFor(url: String): Provider? = PROVIDERS.firstOrNull { it.url.trimEnd('/') == cleanUrl(url) }

    /** Turns any failure into an [AiException] that says what really went wrong. */
    private fun classify(e: Throwable): AiException = when (e) {
        is AiException -> e
        is UnknownHostException -> AiException(AiException.Kind.NETWORK, "DNS failed: cannot find host ${e.message}. Check internet / Private DNS / VPN")
        is SocketTimeoutException -> AiException(AiException.Kind.NETWORK, "Timed out waiting for the server")
        is SSLException -> AiException(AiException.Kind.NETWORK, "Secure connection failed (${e.message}). Check the phone's date and time")
        is IOException -> AiException(AiException.Kind.NETWORK, "${e.javaClass.simpleName}: ${e.message ?: "connection problem"}")
        else -> AiException(AiException.Kind.BAD_REPLY, "${e.javaClass.simpleName}: ${e.message ?: "unexpected"}")
    }

    /** One HTTP round trip. Throws AiException. */
    private fun call(url: String, model: String, key: String, persona: String, question: String): String {
        val base = cleanUrl(url)
        if (base.isEmpty()) throw AiException(AiException.Kind.BAD_URL, "Endpoint URL is empty")
        val needsKey = providerFor(base)?.needsKey ?: false
        if (needsKey && key.isBlank()) throw AiException(AiException.Kind.NO_KEY, "No API key set")
        val target = try { URL("$base/chat/completions") } catch (e: Exception) { throw AiException(AiException.Kind.BAD_URL, "Bad URL: $base") }
        val c = target.openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 60000; c.doOutput = true
            c.instanceFollowRedirects = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", UA)
            if (key.isNotBlank()) c.setRequestProperty("Authorization", "Bearer ${key.trim()}")
            val msgs = JSONArray()
                .put(JSONObject().put("role", "system").put("content", persona))
                .put(JSONObject().put("role", "user").put("content", question))
            val body = JSONObject().put("model", model.trim()).put("messages", msgs).put("max_tokens", 400).put("stream", false)
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val txt = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val apiMsg = runCatching { JSONObject(txt).optJSONObject("error")?.optString("message").orEmpty() }.getOrDefault("")
            val why = if (apiMsg.isNotBlank()) "HTTP $code: ${apiMsg.take(160)}" else "HTTP $code"
            when {
                code in 200..299 -> {}
                code == 401 || code == 403 -> throw AiException(AiException.Kind.BAD_KEY, why)
                code == 402 || code == 429 -> throw AiException(AiException.Kind.LIMIT, why)
                code == 404 -> throw AiException(AiException.Kind.BAD_URL, "$why (check the endpoint URL and model name)")
                else -> throw AiException(AiException.Kind.SERVER, why)
            }
            return extract(txt)
        } finally { c.disconnect() }
    }

    /** Pulls the answer out of a chat-completions reply, tolerating the odd shapes some providers return. */
    private fun extract(txt: String): String {
        val root = try { JSONObject(txt) } catch (e: Exception) {
            throw AiException(AiException.Kind.BAD_REPLY, "Reply was not JSON: ${txt.take(80).replace('\n', ' ')}")
        }
        val choices = root.optJSONArray("choices")
        val msg = choices?.optJSONObject(0)?.optJSONObject("message")
        var content: String? = null
        val raw = msg?.opt("content")
        if (raw is String) content = raw
        else if (raw is JSONArray) content = (0 until raw.length()).joinToString(" ") { raw.optJSONObject(it)?.optString("text").orEmpty() }
        if (content.isNullOrBlank()) content = choices?.optJSONObject(0)?.optString("text")
        if (content.isNullOrBlank()) {
            val err = root.optJSONObject("error")?.optString("message").orEmpty()
            throw AiException(AiException.Kind.BAD_REPLY, if (err.isNotBlank()) err.take(160) else "The reply had no text")
        }
        return content.trim()
    }

    /** Asks once, retrying a single time on a dropped connection (mobile networks often fail the first try). */
    private fun askBlocking(url: String, model: String, key: String, persona: String, question: String): String {
        var last: AiException? = null
        for (attempt in 1..2) {
            try { return call(url, model, key, persona, question) }
            catch (t: Throwable) {
                val e = classify(t)
                last = e
                if (e.kind != AiException.Kind.NETWORK && e.kind != AiException.Kind.SERVER) break
                if (attempt == 1) try { Thread.sleep(700) } catch (ignored: InterruptedException) {}
            }
        }
        throw last ?: AiException(AiException.Kind.NETWORK, "unknown")
    }

    fun ask(url: String, model: String, key: String, persona: String, question: String, onDone: (Result<String>) -> Unit) {
        Thread {
            val r = try { Result.success(askBlocking(url, model, key, persona, question)) }
                    catch (t: Throwable) { Result.failure<String>(classify(t)) }
            Handler(Looper.getMainLooper()).post { onDone(r) }
        }.start()
    }
}
