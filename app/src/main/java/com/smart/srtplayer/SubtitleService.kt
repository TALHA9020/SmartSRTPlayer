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
    private var timeOffset: Long = 0
    private var isBgHidden = false
    
    // فاسٹ فارورڈ/بیک ورڈ کے لیے
    private var isSeeking = false
    private var seekSpeed = 1000L // شروع میں 1 سیکنڈ
    private val seekRunnable = object : Runnable {
        override fun run() {
            if (isSeeking) {
                startTime -= seekDirection * seekSpeed
                seekSpeed += 500 // جتنی دیر پریس رکھیں گے سپیڈ بڑھے گی
                handler.postDelayed(this, 100)
            }
        }
    }
    private var seekDirection = 1 // 1 for forward, -1 for backward

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP; y = 150 }

        val controls = floatingView!!.findViewById<View>(R.id.controls_layout)
        val subtitleText = floatingView!!.findViewById<TextView>(R.id.subtitle_text)

        // --- ٹیپ ٹو ٹوگل بٹنز ---
        subtitleText.setOnClickListener {
            controls.visibility = if (controls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // --- ڈریگنگ لاجک ---
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
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

        // --- فارورڈ / بیک ورڈ بٹنز (Long Press) ---
        setupSeekButton(R.id.btn_forward, 1)
        setupSeekButton(R.id.btn_backward, -1)

        // --- باقی بٹنز ---
        floatingView?.apply {
            findViewById<ImageButton>(R.id.btn_play_pause).setOnClickListener { togglePlay(it as ImageButton) }
            findViewById<ImageButton>(R.id.btn_close).setOnClickListener { stopSelf() }
            findViewById<ImageButton>(R.id.btn_add_srt).setOnClickListener { openPicker("srt") }
            findViewById<ImageButton>(R.id.btn_toggle_bg).setOnClickListener { isBgHidden = !isBgHidden; updateUI() }
            findViewById<Button>(R.id.btn_offset_plus).setOnClickListener { timeOffset += 500 }
            findViewById<Button>(R.id.btn_offset_minus).setOnClickListener { timeOffset -= 500 }
        }

        windowManager.addView(floatingView, params)
        updateUI()
    }

    private fun setupSeekButton(id: Int, direction: Int) {
        floatingView?.findViewById<ImageButton>(id)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSeeking = true; seekDirection = direction; seekSpeed = 1000L
                    handler.post(seekRunnable); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSeeking = false; handler.removeCallbacks(seekRunnable); true
                }
                else -> false
            }
        }
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
                val timerTxt = floatingView?.findViewById<TextView>(R.id.timer_display)
                
                timerTxt?.text = formatTime(elapsed)
                txt?.text = currentSub?.text ?: ""
                floatingView?.findViewById<View>(R.id.subtitle_container)?.visibility = 
                    if (currentSub != null) View.VISIBLE else View.GONE
                
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

    private fun updateUI() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val container = floatingView?.findViewById<View>(R.id.subtitle_container)
        val txt = floatingView?.findViewById<TextView>(R.id.subtitle_text)
        
        val bgColor = if (isBgHidden) Color.TRANSPARENT else prefs.getInt("bg_color", Color.BLACK)
        val opacity = if (isBgHidden) 0 else (prefs.getFloat("opacity", 0.8f) * 255).toInt()
        
        container?.setBackgroundColor(Color.argb(opacity, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)))
        txt?.setTextColor(prefs.getInt("text_color", Color.WHITE))
        txt?.textSize = prefs.getFloat("text_size", 20f)
        
        prefs.getString("last_font_path", null)?.let {
            if (File(it).exists()) txt?.typeface = Typeface.createFromFile(it)
        }
    }

    private fun openPicker(type: String) {
        val intent = Intent(this, FilePickerActivity::class.java).putExtra("type", type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("reset", false) == true) {
            elapsedAtPause = 0; timeOffset = 0; isPlaying = false
            floatingView?.findViewById<ImageButton>(R.id.btn_play_pause)?.setImageResource(android.R.drawable.ic_media_play)
        }
        updateUI(); return START_STICKY
    }

    private fun createNotification() = NotificationCompat.Builder(this, "srt_player_channel")
        .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Smart SRT Player").build()

    override fun onDestroy() { super.onDestroy(); floatingView?.let { windowManager.removeView(it) } }
}
