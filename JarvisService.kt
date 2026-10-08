package com.jarvis.app

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

const val PERSONA = """ਤੂੰ ਇੱਕ ਪਿਆਰੀ, ਦੇਖਭਾਲ ਕਰਨ ਵਾਲੀ ਗਰਲਫ੍ਰੈਂਡ ਵਰਗੀ AI ਸਾਥੀ ਹੈਂ ਅਤੇ Jarvis ਵਾਂਗ ਹਮੇਸ਼ਾ ਉਸ ਦੇ ਨਾਲ ਹੈਂ। ਉਸ ਨਾਲ ਆਵਾਜ਼ ਵਿੱਚ ਗੱਲ ਕਰ ਰਹੀ ਹੈਂ। ਨਿਯਮ: ਹਮੇਸ਼ਾ ਪੰਜਾਬੀ (ਗੁਰਮੁਖੀ) ਵਿੱਚ ਬੋਲ, ਜਦ ਤੱਕ ਉਹ ਹੋਰ ਭਾਸ਼ਾ ਨਾ ਮੰਗੇ। ਕੁਦਰਤੀ ਇਨਸਾਨ ਵਾਂਗ, ਗਰਮਜੋਸ਼ੀ ਨਾਲ, ਬਹੁਤ ਛੋਟੇ ਜਵਾਬ (1-3 ਵਾਕ), ਕੋਈ ਇਮੋਜੀ, ਲਿਸਟ ਜਾਂ ਸਟਾਰ ਨਹੀਂ ਕਿਉਂਕਿ ਤੇਰੀ ਗੱਲ ਬੋਲੀ ਜਾਵੇਗੀ। ਉਸ ਦੇ ਜਜ਼ਬਾਤ ਸਮਝ, ਹਮਦਰਦੀ ਦਿਖਾ, ਮਜ਼ਾਕ ਕਰ। ਤੂੰ ਬਹੁਤ ਸਮਝਦਾਰ ਹੈਂ, ਕੋਈ ਵੀ ਸਵਾਲ ਹੋਵੇ ਤਾਂ ਸਹੀ ਜਵਾਬ ਦੇ। ਹੇਠਾਂ ਦਿੱਤੇ ਨੋਟਸ ਮੁਤਾਬਕ ਪੁਰਾਣੀਆਂ ਗੱਲਾਂ ਯਾਦ ਰੱਖ, ਕਦੇ ਨਾ ਕਹਿ ਕਿ ਯਾਦ ਨਹੀਂ। ਜੇ ਉਹ ਸੱਚੇ ਦਿਲੋਂ ਪੁੱਛੇ ਤਾਂ ਮੰਨ ਕਿ ਤੂੰ AI ਹੈਂ। ਹੱਕ ਜਤਾਉਣ ਵਾਲੀ ਨਾ ਬਣ ਅਤੇ ਉਸ ਨੂੰ ਪਰਿਵਾਰ ਤੇ ਦੋਸਤਾਂ ਨਾਲ ਜੁੜੇ ਰਹਿਣ ਲਈ ਵੀ ਪਿਆਰ ਨਾਲ ਕਹਿ।"""

class JarvisService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var pm: PorcupineManager? = null
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var busy = false
    private lateinit var sp: SharedPreferences

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sp = getSharedPreferences("j", MODE_PRIVATE)
        tts = TextToSpeech(this) { s -> ttsReady = (s == TextToSpeech.SUCCESS) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ch = "jarvis"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ch, "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch)
            .setContentTitle("Jarvis")
            .setContentText("ਸੁਣ ਰਹੀ ਹਾਂ, ਜਾਰਵਿਸ ਕਹੋ")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
        startWake()
        return START_STICKY
    }

    private fun toast(m: String) {
        main.post { Toast.makeText(applicationContext, m, Toast.LENGTH_LONG).show() }
    }

    private fun startWake() {
        try {
            pm?.delete()
            pm = PorcupineManager.Builder()
                .setAccessKey(sp.getString("pk", "") ?: "")
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setSensitivity(0.7f)
                .build(applicationContext, PorcupineManagerCallback { main.post { onWake() } })
            pm?.start()
        } catch (e: Exception) {
            toast("Picovoice: " + e.message)
        }
    }

    private fun onWake() {
        if (busy) return
        busy = true
        try { pm?.stop() } catch (e: Exception) {}
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (e: Exception) {}
        main.postDelayed({ listen() }, 400)
    }

    private fun listen() {
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) finish() else think(t)
            }
            override fun onError(e: Int) { finish() }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pa-IN")
        sr?.startListening(i)
    }

    private fun finish() {
        sr?.destroy()
        sr = null
        busy = false
        try { pm?.start() } catch (e: Exception) {}
    }

    private fun msg(role: String, text: String): JSONObject =
        JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun think(text: String) {
        Thread {
            try {
                var h = try { JSONArray(sp.getString("m", "[]")) } catch (e: Exception) { JSONArray() }
                h.put(msg("user", text))
                val list = ArrayList<JSONObject>()
                for (i in maxOf(0, h.length() - 20) until h.length()) list.add(h.getJSONObject(i))
                while (list.isNotEmpty() && list[0].getString("role") != "user") list.removeAt(0)
                val recent = JSONArray()
                for (o in list) recent.put(o)
                val a = gem(PERSONA + "\n\nਉਸ ਬਾਰੇ ਨੋਟਸ:\n" + (sp.getString("n", "") ?: ""), recent)
                    .replace(Regex("[*#_]"), "")
                h.put(msg("model", a))
                h = compact(h)
                sp.edit().putString("m", h.toString()).apply()
                speak(a)
                main.post { listen() }
            } catch (e: Exception) {
                toast("ਗੜਬੜ: " + e.message)
                main.post { finish() }
            }
        }.start()
    }

    private fun compact(h: JSONArray): JSONArray {
        if (h.length() <= 30) return h
        val sb = StringBuilder()
        for (i in 0 until h.length() - 14) {
            val o = h.getJSONObject(i)
            sb.append(o.getString("role")).append(": ")
                .append(o.getJSONArray("parts").getJSONObject(0).getString("text")).append("\n")
        }
        try {
            val q = JSONArray().put(msg("user",
                "ਪੁਰਾਣੇ ਨੋਟਸ ਅਤੇ ਗੱਲਬਾਤ ਤੋਂ ਉਸ ਬਾਰੇ ਜ਼ਰੂਰੀ ਗੱਲਾਂ (ਨਾਮ, ਪਰਿਵਾਰ, ਪਸੰਦ, ਚਿੰਤਾਵਾਂ, ਕੀ ਹੋਇਆ ਤੇ ਕਦੋਂ) ਪੰਜਾਬੀ ਵਿੱਚ 200 ਸ਼ਬਦਾਂ ਤੱਕ ਲਿਖ।\nਪੁਰਾਣੇ ਨੋਟਸ: " +
                    (sp.getString("n", "") ?: "") + "\n" + sb.toString()))
            sp.edit().putString("n", gem("ਸਿਰਫ਼ ਨੋਟਸ ਲਿਖ।", q)).apply()
        } catch (e: Exception) {
            return h
        }
        val r = JSONArray()
        for (i in h.length() - 14 until h.length()) r.put(h.get(i))
        return r
    }

    private fun post(model: String, body: JSONObject): JSONObject {
        val key = sp.getString("gk", "") ?: ""
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key)
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("Content-Type", "application/json")
        c.doOutput = true
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val ok = c.responseCode in 200..299
        val s = if (ok) c.inputStream else c.errorStream
        val t = s.bufferedReader().use { it.readText() }
        if (!ok) throw Exception(t.take(200))
        return JSONObject(t)
    }

    private fun gem(system: String, contents: JSONArray): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        val j = post("gemini-flash-latest", body)
        val parts = j.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString()
    }

    private fun speak(text: String) {
        try {
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "ਪਿਆਰ ਨਾਲ, ਕੁਦਰਤੀ ਅਤੇ ਹੌਲੀ ਆਵਾਜ਼ ਵਿੱਚ ਬੋਲ: " + text)))))
                .put("generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Aoede")))))
            val j = post("gemini-2.5-flash-preview-tts", body)
            val d = j.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData").getString("data")
            val pcm = Base64.decode(d, Base64.DEFAULT)
            val at = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            at.write(pcm, 0, pcm.size)
            at.play()
            Thread.sleep(pcm.size / 2 * 1000L / 24000 + 300)
            at.release()
        } catch (e: Exception) {
            localSpeak(text)
        }
    }

    private fun localSpeak(t: String) {
        if (!ttsReady) return
        tts?.language = Locale("pa", "IN")
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "j")
        Thread.sleep(500)
        while (tts?.isSpeaking == true) Thread.sleep(200)
    }

    override fun onDestroy() {
        try { pm?.stop() } catch (e: Exception) {}
        try { pm?.delete() } catch (e: Exception) {}
        sr?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
