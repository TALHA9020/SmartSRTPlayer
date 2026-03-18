package com.smart.srtplayer

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import java.io.File

class SubtitleService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var startTime: Long = 0
    private var isPlaying = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "subtitle_service")
            .setContentTitle("Smart SRT Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notification)
        setupFloatingWindow()
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        
        try {
            floatingView = inflater.inflate(R.layout.overlay_layout, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP
            windowManager.addView(floatingView, params)
            
            updateUIFromPrefs()
            startSubtitleTimer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateUIFromPrefs() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        floatingView?.let { view ->
            val container = view.findViewById<FrameLayout>(R.id.subtitle_container)
            val textSub = view.findViewById<TextView>(R.id.subtitle_text)
            val textTimer = view.findViewById<TextView>(R.id.timer_text)

            // فونٹس اور سیٹنگز لوڈ کریں
            val textSize = prefs.getFloat("text_size", 1.0f)
            val timerSize = prefs.getFloat("timer_size", 0.8f)
            val opacity = prefs.getFloat("opacity", 0.8f)
            val textColor = prefs.getInt("text_color", -1) // Default White
            val bgColor = prefs.getInt("bg_color", -16777216) // Default Black
            val fontPath = prefs.getString("last_font_path", null)

            textSub.textSize = 20 * textSize
            textTimer.textSize = 14 * timerSize
            textSub.setTextColor(textColor)
            textTimer.setTextColor(textColor)
            container.alpha = opacity
            container.setBackgroundColor(bgColor)

            // کسٹم فونٹ اپلائی کریں
            fontPath?.let {
                val fontFile = File(it)
                if (fontFile.exists()) {
                    textSub.typeface = Typeface.createFromFile(fontFile)
                }
            }

            // چوڑائی سیٹ کریں
            val bgWidth = prefs.getFloat("bg_width", 1.0f)
            val layoutParams = container.layoutParams
            layoutParams.width = (resources.displayMetrics.widthPixels * bgWidth).toInt()
            container.layoutParams = layoutParams
        }
    }

    private fun startSubtitleTimer() {
        if (isPlaying) return
        startTime = System.currentTimeMillis()
        isPlaying = true
        
        handler.post(object : Runnable {
            override fun run() {
                if (!isPlaying) return
                val elapsed = System.currentTimeMillis() - startTime
                
                // موجودہ سب ٹائٹل تلاش کریں
                val currentSub = MainActivity.fullSubtitleList.find { it.start <= elapsed && it.end >= elapsed }

                floatingView?.let { view ->
                    view.findViewById<TextView>(R.id.subtitle_text).text = currentSub?.text ?: ""
                    view.findViewById<TextView>(R.id.timer_text).text = formatTime(elapsed)
                }
                handler.postDelayed(this, 100)
            }
        })
    }

    private fun formatTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / 60000) % 60
        val h = (ms / 3600000)
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateUIFromPrefs()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isPlaying = false
        floatingView?.let { windowManager.removeView(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("subtitle_service", "Subtitle Player", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
