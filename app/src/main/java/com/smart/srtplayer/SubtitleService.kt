package com.smart.srtplayer

import android.app.*
import android.content.*
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        setupFloatingWindow()
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            y = 100
        }

        // ڈریگنگ لاجک
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        // کلک کرنے پر بٹنز دکھائیں
                        floatingView?.findViewById<View>(R.id.controls_layout)?.visibility = View.VISIBLE
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        // بٹن ایکشنز
        floatingView?.findViewById<ImageButton>(R.id.btn_play_pause)?.setOnClickListener { togglePlay(it as ImageButton) }
        floatingView?.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener { stopSelf() }
        floatingView?.findViewById<ImageButton>(R.id.btn_pick_srt)?.setOnClickListener { openPicker("srt") }
        floatingView?.findViewById<ImageButton>(R.id.btn_pick_font)?.setOnClickListener { openPicker("font") }

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
                val elapsed = System.currentTimeMillis() - startTime
                val currentSub = MainActivity.fullSubtitleList.find { it.start <= elapsed && it.end >= elapsed }
                
                val txt = floatingView?.findViewById<TextView>(R.id.subtitle_text)
                val container = floatingView?.findViewById<View>(R.id.subtitle_container)
                
                if (currentSub != null) {
                    txt?.text = currentSub.text
                    container?.visibility = View.VISIBLE
                } else {
                    txt?.text = ""
                    container?.visibility = View.GONE // جب ٹیکسٹ نہ ہو تو کالا ڈبہ غائب
                }
                handler.postDelayed(this, 100)
            }
        })
    }

    private fun updateUI() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val container = floatingView?.findViewById<View>(R.id.subtitle_container)
        val txt = floatingView?.findViewById<TextView>(R.id.subtitle_text)
        
        val bgColor = prefs.getInt("bg_color", Color.BLACK)
        val opacity = (prefs.getFloat("opacity", 0.8f) * 255).toInt()
        val textColor = prefs.getInt("text_color", Color.WHITE)
        val textSize = prefs.getFloat("text_size", 20f)

        container?.setBackgroundColor(Color.argb(opacity, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)))
        txt?.setTextColor(textColor)
        txt?.textSize = textSize

        // فونٹ اپلائی کریں
        prefs.getString("last_font_path", null)?.let {
            if (File(it).exists()) txt?.typeface = Typeface.createFromFile(it)
        }
    }

    private fun openPicker(type: String) {
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra("type", type)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun createNotification(): Notification {
        val channelId = "smart_srt_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SRT Player", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Smart SRT Player Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateUI()
        // اگر نئی SRT فائل آئی ہے تو ٹائمر زیرو کریں
        if (intent?.getBooleanExtra("reset", false) == true) {
            elapsedAtPause = 0
            isPlaying = false
            floatingView?.findViewById<ImageButton>(R.id.btn_play_pause)?.setImageResource(android.R.drawable.ic_media_play)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isPlaying = false
        floatingView?.let { windowManager.removeView(it) }
    }
}
