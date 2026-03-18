package com.smart.srtplayer

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import java.io.File
import kotlin.math.roundToInt

class SubtitleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var currentIndex = mutableIntStateOf(0)
    private var isPlaying = mutableStateOf(false)
    private var currentTimeMs = 0L
    private val handler = Handler(Looper.getMainLooper())

    private var isBgVisible = mutableStateOf(true)
    private var isControlsFolded = mutableStateOf(false)

    private val ticker = object : Runnable {
        override fun run() {
            if (isPlaying.value && MainActivity.fullSubtitleList.isNotEmpty()) {
                currentTimeMs += 100
                val list = MainActivity.fullSubtitleList
                
                if (currentIndex.intValue < list.size) {
                    // Check if current subtitle ended or next one started
                    val foundIndex = list.indexOfFirst { currentTimeMs >= it.start && currentTimeMs <= it.end }
                    if (foundIndex != -1 && foundIndex != currentIndex.intValue) {
                        currentIndex.intValue = foundIndex
                        saveProgress() // ہر سب ٹائٹل پر پروگریس سیو کریں
                    }
                }
                handler.postDelayed(this, 100)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // لوڈ کریں کہ کہاں چھوڑا تھا
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        currentIndex.intValue = prefs.getInt("last_index", 0)
        if (MainActivity.fullSubtitleList.isNotEmpty() && currentIndex.intValue < MainActivity.fullSubtitleList.size) {
            currentTimeMs = MainActivity.fullSubtitleList[currentIndex.intValue].start
        }
        
        startMyForeground()
        showFloatingUI()
    }

    private fun saveProgress() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("last_index", currentIndex.intValue).apply()
    }

    private fun startMyForeground() {
        val chan = NotificationChannel("sub_chan", "Subtitles", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        startForeground(1, NotificationCompat.Builder(this, "sub_chan")
            .setContentTitle("Smart SRT Player Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build())
    }

    private fun showFloatingUI() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val fontPath = prefs.getString("last_font_path", null)
        val windowSizeMultiplier = prefs.getFloat("window_size", 1.0f)
        val textCol = Color(prefs.getInt("text_color", Color.White.toArgb()))
        val bgCol = Color(prefs.getInt("bg_color", Color.Black.toArgb()))
        val opac = prefs.getFloat("opacity", 0.8f)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, // تبدیل شدہ: میچ پیرنٹ کے بجائے ریپ کنٹینٹ
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 500 
        }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SubtitleService)
            setViewTreeSavedStateRegistryOwner(this@SubtitleService)
            setContent {
                val font = if (fontPath != null) FontFamily(Font(File(fontPath))) else FontFamily.Default
                val idx by currentIndex
                val active by isPlaying
                val bgVisible by isBgVisible
                val controlsFolded by isControlsFolded
                
                val currentText = if (MainActivity.fullSubtitleList.isNotEmpty() && idx < MainActivity.fullSubtitleList.size) 
                    MainActivity.fullSubtitleList[idx].text else "ختم یا خالی"

                val finalBgColor = if (bgVisible) bgCol.copy(alpha = opac) else Color.Transparent

                // مین ڈریگ ایبل باکس
                Box(modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            params.x += dragAmount.x.toInt()
                            params.y += dragAmount.y.toInt()
                            windowManager.updateViewLayout(floatingView, params)
                        }
                    }
                    .background(finalBgColor, RoundedCornerShape(16.dp))
                    .padding(if (controlsFolded) 8.dp else 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(IntrinsicSize.Min).padding(horizontal = 10.dp)
                    ) {
                        // سب ٹائٹل ٹیکسٹ
                        Text(
                            text = currentText, 
                            color = textCol, 
                            fontSize = (22 * windowSizeMultiplier).sp, 
                            fontFamily = font, 
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickable { isControlsFolded.value = !isControlsFolded.value }
                        )
                        
                        if (!controlsFolded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if(currentIndex.intValue > 0) { 
                                    currentIndex.intValue-- 
                                    currentTimeMs = MainActivity.fullSubtitleList[currentIndex.intValue].start
                                    saveProgress()
                                }}) { Icon(Icons.Default.SkipPrevious, "", tint = textCol, modifier = Modifier.size(20.dp)) }

                                FloatingActionButton(
                                    onClick = { 
                                        isPlaying.value = !isPlaying.value
                                        if(isPlaying.value) handler.post(ticker) else handler.removeCallbacks(ticker)
                                    }, 
                                    containerColor = Color(0xFFFFD700), 
                                    shape = CircleShape, 
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(if(active) Icons.Default.Pause else Icons.Default.PlayArrow, "", tint = Color.Black)
                                }

                                IconButton(onClick = { if(currentIndex.intValue < MainActivity.fullSubtitleList.size - 1) {
                                    currentIndex.intValue++
                                    currentTimeMs = MainActivity.fullSubtitleList[currentIndex.intValue].start
                                    saveProgress()
                                }}) { Icon(Icons.Default.SkipNext, "", tint = textCol, modifier = Modifier.size(20.dp)) }
                                
                                IconButton(onClick = { isBgVisible.value = !isBgVisible.value }) { 
                                    Icon(if(bgVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "", tint = textCol, modifier = Modifier.size(20.dp)) 
                                }
                                
                                IconButton(onClick = { stopSelf() }) { Icon(Icons.Default.Close, "", tint = Color.Red, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                }
            }
        }
        windowManager.addView(floatingView, params)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        saveProgress()
        floatingView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(i: Intent?) = null
}
