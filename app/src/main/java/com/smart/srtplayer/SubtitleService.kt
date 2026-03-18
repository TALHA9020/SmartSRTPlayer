package com.smart.srtplayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat

// اگر اوپر والا امپورٹ کام نہیں کر رہا تو ہم براہ راست ریفرنس استعمال کریں گے
typealias SubR = com.smart.srtplayer.R

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
            .setContentTitle("Smart SRT")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notification)
        setupFloatingWindow()
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        
        try {
            // ہم نے یہاں SubR استعمال کیا ہے تاکہ امپورٹ کا مسئلہ نہ ہو
            floatingView = inflater.inflate(SubR.layout.overlay_layout, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP
            windowManager.addView(floatingView, params)
            updateUIFromPrefs()
            startSubtitleTimer()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun updateUIFromPrefs() {
        val prefs = getSharedPreferences("SmartPrefs", MODE_PRIVATE)
        floatingView?.let { view ->
            val container = view.findViewById<FrameLayout>(SubR.id.subtitle_container)
            val textSub = view.findViewById<TextView>(SubR.id.subtitle_text)
            val textTimer = view.findViewById<TextView>(SubR.id.timer_text)

            val textSize = prefs.getFloat("text_size", 1.0f)
            val timerSize = prefs.getFloat("timer_size", 0.8f)
            val opacity = prefs.getFloat("opacity", 0.8f)
            val textColor = prefs.getInt("text_color", Color.White.toArgb())
            val bgColor = prefs.getInt("bg_color", Color.Black.toArgb())
            val bgWidth = prefs.getFloat("bg_width", 1.0f)

            textSub.textSize = 18 * textSize
            textTimer.textSize = 14 * timerSize
            textSub.setTextColor(textColor)
            textTimer.setTextColor(textColor)
            container.alpha = opacity
            container.setBackgroundColor(bgColor)

            val layoutParams = container.layoutParams
            layoutParams.width = (resources.displayMetrics.widthPixels * bgWidth).toInt()
            container.layoutParams = layoutParams
        }
    }

    private fun startSubtitleTimer() {
        startTime = System.currentTimeMillis()
        isPlaying = true
        handler.post(object : Runnable {
            override fun run() {
                if (!isPlaying) return
                val elapsed = System.currentTimeMillis() - startTime
                
                val currentSub = MainActivity.fullSubtitleList.find { sub -> 
                    elapsed >= sub.start && elapsed <= sub.end 
                }

                floatingView?.let { view ->
                    view.findViewById<TextView>(SubR.id.subtitle_text).text = currentSub?.text ?: ""
                    view.findViewById<TextView>(SubR.id.timer_text).text = formatTime(elapsed)
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
            val channel = NotificationChannel("subtitle_service", "Subtitle Service", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }
}
