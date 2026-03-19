package com.smart.srtplayer

import android.annotation.SuppressLint
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
    private lateinit var params: WindowManager.LayoutParams
    
    private val handler = Handler(Looper.getMainLooper())
    private val seekHandler = Handler(Looper.getMainLooper())
    
    private var startTime: Long = 0
    private var isPlaying = false
    private var elapsedAtPause: Long = 0
    private var isBgHidden = false
    private var controlsVisible = false
    private var isSeeking = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        setupFloatingWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("srt_service", "Subtitle Player", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingWindow() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 100 
        }

        val subText = floatingView!!.findViewById<TextView>(R.id.subtitle_text)
        val controls = floatingView!!.findViewById<View>(R.id.controls_layout)

        // ڈریگنگ اور ٹیپ ٹو شو کنٹرولز
        subText.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(floatingView, params)
                            isMoved = true
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoved) {
                            controlsVisible = !controlsVisible
                            controls.visibility = if (controlsVisible) View.VISIBLE else View.GONE
                        }
                        return true
                    }
                }
                return false
            }
        })

        // بٹن کنٹرولز
        floatingView?.apply {
            findViewById<ImageButton>(R.id.btn_play_pause).setOnClickListener { togglePlay(it as ImageButton) }
            findViewById<ImageButton>(R.id.btn_close).setOnClickListener { stopSelf() }
            findViewById<ImageButton>(R.id.btn_toggle_bg).setOnClickListener { 
                isBgHidden = !isBgHidden
                updateUI() 
            }
            findViewById<ImageButton>(R.id.btn_playlist).setOnClickListener { 
                val intent = Intent(this@SubtitleService, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }

            setupSeek(findViewById(R.id.btn_forward), 1)
            setupSeek(findViewById(R.id.btn_backward), -1)
        }

        windowManager.addView(floatingView, params)
        updateUI()
        startUpdating() // ٹائمر کو ہر وقت ایکٹیو رکھنا
    }

    private fun startUpdating() {
        handler.post(object : Runnable {
            override fun run() {
                if (isPlaying && !isSeeking) {
                    val elapsed = System.currentTimeMillis() - startTime
                    elapsedAtPause = elapsed
                    updateTimerDisplay()

                    // لسٹ سے صحیح سب ٹائٹل نکالنا
                    val currentSub = MainActivity.currentSubtitleList.find { 
                        elapsed >= it.start && elapsed <= it.end 
                    }
                    
                    floatingView?.findViewById<TextView>(R.id.subtitle_text)?.text = 
                        currentSub?.text ?: ""
                }
                handler.postDelayed(this, 100)
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSeek(btn: ImageButton, direction: Int) {
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSeeking = true
                    startSeeking(direction)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSeeking = false
                    seekHandler.removeCallbacksAndMessages(null)
                    if (isPlaying) startTime = System.currentTimeMillis() - elapsedAtPause
                    true
                }
                else -> false
            }
        }
    }

    private fun startSeeking(direction: Int) {
        val seekRunnable = object : Runnable {
            var jump = 1000L // ہر بار 1 سیکنڈ آگے/پیچھے
            override fun run() {
                if (!isSeeking) return
                elapsedAtPause += (jump * direction)
                if (elapsedAtPause < 0) elapsedAtPause = 0
                updateTimerDisplay()
                seekHandler.postDelayed(this, 150)
            }
        }
        seekHandler.post(seekRunnable)
    }

    private fun togglePlay(btn: ImageButton) {
        isPlaying = !isPlaying
        if (isPlaying) {
            // اگر لسٹ خالی ہے تو پہلے پچھلی فائل لوڈ کرنے کی کوشش کریں
            if (MainActivity.currentSubtitleList.isEmpty()) {
                val lastPath = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE).getString("last_srt_path", null)
                lastPath?.let { MainActivity.loadSubtitle(this, it) }
            }
            startTime = System.currentTimeMillis() - elapsedAtPause
            btn.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            elapsedAtPause = System.currentTimeMillis() - startTime
            btn.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun updateTimerDisplay() {
        val seconds = (elapsedAtPause / 1000) % 60
        val minutes = (elapsedAtPause / (1000 * 60)) % 60
        val hours = (elapsedAtPause / (1000 * 60 * 60)) % 24
        floatingView?.findViewById<TextView>(R.id.timer_display)?.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun updateUI() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val container = floatingView?.findViewById<View>(R.id.subtitle_container)
        val txt = floatingView?.findViewById<TextView>(R.id.subtitle_text)

        txt?.textSize = prefs.getFloat("text_size", 20f)
        val alpha = if (isBgHidden) 0 else (prefs.getFloat("opacity", 0.8f) * 255).toInt()
        container?.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
        
        prefs.getString("last_font_path", null)?.let { path ->
            val file = File(path)
            if (file.exists()) txt?.typeface = Typeface.createFromFile(file)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "srt_service")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Smart SRT Player")
            .setContentText("Player is ready")
            .build()
        startForeground(1, notification)
        updateUI()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        floatingView?.let { windowManager.removeView(it) }
    }
}
