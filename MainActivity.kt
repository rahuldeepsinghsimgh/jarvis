package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var st: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sp = getSharedPreferences("j", MODE_PRIVATE)
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(40, 100, 40, 40)
        val t = TextView(this)
        t.text = "Jarvis"
        t.textSize = 28f
        val gk = EditText(this)
        gk.hint = "Gemini API key"
        gk.setText(sp.getString("gk", ""))
        val pk = EditText(this)
        pk.hint = "Picovoice AccessKey"
        pk.setText(sp.getString("pk", ""))
        st = TextView(this)
        st.textSize = 16f
        val go = Button(this)
        go.text = "ਚਾਲੂ ਕਰੋ"
        val stop = Button(this)
        stop.text = "ਬੰਦ ਕਰੋ"
        val clr = Button(this)
        clr.text = "ਯਾਦਾਂ ਮਿਟਾਓ"
        go.setOnClickListener {
            sp.edit().putString("gk", gk.text.toString().trim()).putString("pk", pk.text.toString().trim()).apply()
            if (gk.text.isBlank() || pk.text.isBlank()) {
                st.text = "ਦੋਵੇਂ key ਪਾਓ"
            } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, "android.permission.POST_NOTIFICATIONS"), 1)
            } else {
                begin()
            }
        }
        stop.setOnClickListener {
            stopService(Intent(this, JarvisService::class.java))
            st.text = "ਬੰਦ ਹੈ"
        }
        clr.setOnClickListener {
            sp.edit().remove("m").remove("n").apply()
            st.text = "ਯਾਦਾਂ ਮਿਟ ਗਈਆਂ"
        }
        l.addView(t)
        l.addView(gk)
        l.addView(pk)
        l.addView(go)
        l.addView(stop)
        l.addView(clr)
        l.addView(st)
        val sv = ScrollView(this)
        sv.addView(l)
        setContentView(sv)
    }

    private fun begin() {
        startForegroundService(Intent(this, JarvisService::class.java))
        st.text = "ਚਾਲੂ ਹੈ। ਹੁਣ 'ਜਾਰਵਿਸ' ਕਹੋ।"
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else st.text = "ਮਾਈਕ ਦੀ ਇਜਾਜ਼ਤ ਚਾਹੀਦੀ ਹੈ"
    }
}
