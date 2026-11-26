package com.pcl.chronoslock

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Music-safe microphone. Android's SpeechRecognizer takes AUDIO FOCUS, which is what pauses your music every time she
 * listens. This reads the mic directly with AudioRecord (no audio focus at all) and recognises speech offline with Vosk,
 * so music, podcasts and videos keep playing. The small English model (~40 MB) is downloaded once from the app.
 */
object VoskMic {
    enum class State { MISSING, DOWNLOADING, READY, ERROR }

    private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"

    val state = MutableStateFlow(State.MISSING)
    val progress = MutableStateFlow(0f)
    val message = MutableStateFlow("")

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null

    class Run { @Volatile var stop = false }

    private fun modelDir(ctx: Context) = File(ctx.filesDir, "vosk/$MODEL_NAME")
    private fun installed(ctx: Context): Boolean = File(modelDir(ctx), "am").isDirectory && File(modelDir(ctx), "conf").isDirectory

    fun init(ctx: Context) { if (state.value != State.DOWNLOADING) state.value = if (installed(ctx)) State.READY else State.MISSING }
    fun ready() = state.value == State.READY

    /** Downloads and unpacks the speech model. Safe to call again after a failure. */
    fun download(ctx: Context, onDone: (Boolean) -> Unit) {
        if (state.value == State.DOWNLOADING) return
        val app = ctx.applicationContext
        state.value = State.DOWNLOADING; progress.value = 0f; message.value = "Connecting…"
        Thread {
            var ok = false
            try {
                val zip = File(app.cacheDir, "vosk-model.zip")
                val c = URL(MODEL_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 20000; c.readTimeout = 30000; c.instanceFollowRedirects = true
                if (c.responseCode !in 200..299) throw IllegalStateException("Server answered HTTP ${c.responseCode}")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: 40_000_000L
                var done = 0L
                c.inputStream.use { inp -> FileOutputStream(zip).use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        val p = (done.toFloat() / total).coerceIn(0f, 0.9f)
                        main.post { progress.value = p; message.value = "Downloading… ${done / 1_000_000} MB" }
                    }
                } }
                c.disconnect()
                main.post { message.value = "Unpacking…"; progress.value = 0.92f }
                val tmp = File(app.filesDir, "vosk_tmp"); tmp.deleteRecursively(); tmp.mkdirs()
                val tmpPath = tmp.canonicalPath
                ZipInputStream(zip.inputStream().buffered()).use { zin ->
                    while (true) {
                        val e = zin.nextEntry ?: break
                        val f = File(tmp, e.name)
                        if (!f.canonicalPath.startsWith(tmpPath)) continue          // never write outside the folder
                        if (e.isDirectory) f.mkdirs() else {
                            f.parentFile?.mkdirs()
                            FileOutputStream(f).use { zin.copyTo(it) }
                        }
                    }
                }
                val target = modelDir(app)
                target.deleteRecursively(); target.parentFile?.mkdirs()
                if (!File(tmp, MODEL_NAME).renameTo(target)) throw IllegalStateException("Could not install the model")
                tmp.deleteRecursively(); zip.delete()
                model = null
                ok = true
            } catch (t: Throwable) {
                main.post { message.value = "Failed: ${t.message ?: t.javaClass.simpleName}" }
            }
            main.post {
                state.value = if (ok) State.READY else State.ERROR
                if (ok) { progress.value = 1f; message.value = "Installed" }
                onDone(ok)
            }
        }.start()
    }

    fun delete(ctx: Context) {
        model = null
        File(ctx.filesDir, "vosk").deleteRecursively()
        state.value = State.MISSING; message.value = ""; progress.value = 0f
    }

    @Synchronized private fun loadModel(ctx: Context): Model? {
        model?.let { return it }
        if (!installed(ctx)) return null
        return Model(modelDir(ctx).absolutePath).also { model = it }
    }

    /**
     * Starts listening on a background thread. Callbacks arrive on the main thread. Call [Run.stop] to end it.
     * Partial and final texts are plain lower-case words.
     */
    @SuppressLint("MissingPermission")
    fun start(ctx: Context, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onLevel: (Float) -> Unit, onFail: (String) -> Unit): Run {
        val run = Run()
        val app = ctx.applicationContext
        Thread {
            var rec: AudioRecord? = null
            var r: Recognizer? = null
            try {
                val m = loadModel(app) ?: throw IllegalStateException("Speech model not installed")
                val rate = 16000
                val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val size = maxOf(min, 8192)
                var ar = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
                if (ar.state != AudioRecord.STATE_INITIALIZED) {
                    ar.release()
                    ar = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
                }
                rec = ar
                if (ar.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Microphone unavailable")
                val rr = Recognizer(m, rate.toFloat())
                r = rr
                ar.startRecording()
                val buf = ByteArray(4096)
                var lastPartial = ""
                while (!run.stop) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n < 0) throw IllegalStateException("Microphone read error $n")
                    if (n == 0) continue
                    // loudness for the visualiser
                    var sum = 0.0
                    var i = 0
                    while (i + 1 < n) { val v = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8); sum += (v * v).toDouble(); i += 2 }
                    val rms = sqrt(sum / (n / 2).coerceAtLeast(1))
                    val lvl = (((20.0 * log10(rms + 1.0)) - 30.0) / 40.0).toFloat().coerceIn(0f, 1f)
                    main.post { if (!run.stop) onLevel(lvl) }
                    if (rr.acceptWaveForm(buf, n)) {
                        val t = JSONObject(rr.result).optString("text")
                        lastPartial = ""
                        if (t.isNotBlank()) main.post { if (!run.stop) onFinal(t) }
                    } else {
                        val p = JSONObject(rr.partialResult).optString("partial")
                        if (p.isNotBlank() && p != lastPartial) { lastPartial = p; main.post { if (!run.stop) onPartial(p) } }
                    }
                }
            } catch (t: Throwable) {
                if (!run.stop) main.post { onFail(t.message ?: t.javaClass.simpleName) }
            } finally {
                runCatching { rec?.stop() }; runCatching { rec?.release() }; runCatching { r?.close() }
            }
        }.start()
        return run
    }
}
