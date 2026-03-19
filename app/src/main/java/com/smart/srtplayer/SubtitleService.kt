package com.smart.srtplayer

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import java.io.File

class SubtitleService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private var startTime: Long = 0
    private var isPlaying = false
    private var elapsedAtPause: Long = 0
    private var timeOffset: Long = 0
    private var isBgHidden = false
    
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupFloatingWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "srt_player_channel",
                "Subtitle Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            y = 150 
        }

        floatingView?.findViewById<TextView>(R.id.subtitle_text)?.setOnClickListener {
            val controls = floatingView!!.findViewById<View>(R.id.controls_layout)
            controls.visibility = if (controls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        floatingView?.apply {
            findViewById<ImageButton>(R.id.btn_play_pause).setOnClickListener { togglePlay(it as ImageButton) }
            findViewById<ImageButton>(R.id.btn_close).setOnClickListener { stopSelf() }
            findViewById<ImageButton>(R.id.btn_toggle_bg).setOnClickListener { isBgHidden = !isBgHidden; updateUI() }
        }

        windowManager.addView(floatingView, params)
        updateUI()
    }

    private fun togglePlay(btn: ImageButton) {
        isPlaying = !isPlaying
        if (isPlaying) {
            startTime = System.currentTimeMillis() - elapsedAtPause
            btn.setImageResource(android.R.drawable.ic_media_pause)
            runTimer()
        } else {
            elapsedAtPause = System.currentTimeMillis() - startTime
            btn.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun runTimer() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isPlaying) return
                val elapsed = (System.currentTimeMillis() - startTime) + timeOffset
                val currentSub = MainActivity.fullSubtitleList.find { it.start <= elapsed && it.end >= elapsed }
                
                val txt = floatingView?.findViewById<TextView>(R.id.subtitle_text)
                txt?.text = currentSub?.text ?: ""
                handler.postDelayed(this, 100)
            }
        })
    }

    private fun updateUI() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val container = floatingView?.findViewById<View>(R.id.subtitle_container)
        val txt = floatingView?.findViewById<TextView>(R.id.subtitle_text)
        
        val bgColor = if (isBgHidden) Color.TRANSPARENT else prefs.getInt("bg_color", Color.BLACK)
        val opacity = if (isBgHidden) 0 else (prefs.getFloat("opacity", 0.8f) * 255).toInt()
        
        container?.setBackgroundColor(Color.argb(opacity, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)))
        txt?.setTextColor(prefs.getInt("text_color", Color.WHITE))
        txt?.textSize = prefs.getFloat("text_size", 20f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
        updateUI()
        return START_STICKY
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "srt_player_channel")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Smart SRT Player")
            .setContentText("پلیئر چل رہا ہے")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager.removeView(it) }
    }
}
