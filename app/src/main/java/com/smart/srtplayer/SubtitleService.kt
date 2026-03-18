package com.smart.srtplayer

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import java.io.File
import kotlinx.coroutines.*

class SubtitleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var currentIndex = mutableIntStateOf(-1)
    private var isPlaying = mutableStateOf(false)
    private var baseTimeMs = mutableLongStateOf(0L)
    private var timeOffsetMs = mutableLongStateOf(0L)
    
    private var lastSystemTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var isBgVisible = mutableStateOf(true)
    private var isControlsFolded = mutableStateOf(false)

    private val ticker = object : Runnable {
        override fun run() {
            if (isPlaying.value) {
                val now = SystemClock.elapsedRealtime()
                if (lastSystemTime != 0L) {
                    val diff = now - lastSystemTime
                    baseTimeMs.longValue += diff
                }
                lastSystemTime = now
                updateIndexByTime(baseTimeMs.longValue + timeOffsetMs.longValue)
                saveProgress()
                handler.postDelayed(this, 50) 
            }
        }
    }

    private fun updateIndexByTime(totalTime: Long) {
        val list = MainActivity.fullSubtitleList
        val foundIndex = list.indexOfFirst { totalTime >= it.start && totalTime <= it.end }
        currentIndex.intValue = foundIndex 
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        baseTimeMs.longValue = prefs.getLong("last_time_ms", 0L)
        timeOffsetMs.longValue = prefs.getLong("last_offset_ms", 0L)
        
        // لوڈ کرنے کی منطق اگر سروس دوبارہ شروع ہو
        if (MainActivity.fullSubtitleList.isEmpty()) {
            val srtPath = prefs.getString("last_srt_path", null)
            srtPath?.let { 
                MainActivity.fullSubtitleList.clear()
                val file = File(it)
                if (file.exists()) {
                    MainActivity.fullSubtitleList.addAll(parseSrt(file.readText())) 
                }
            }
        }

        startMyForeground()
        showFloatingUI()
    }

    private fun parseSrt(content: String): List<SubtitleItem> {
        val list = mutableListOf<SubtitleItem>()
        try {
            val blocks = content.split(Regex("(\\n\\n)|(\\r\\n\\r\\n)"))
            for (block in blocks) {
                val lines = block.trim().lines()
                if (lines.size >= 3) {
                    val timeRange = lines[1].split(" --> ")
                    if (timeRange.size == 2) {
                        list.add(SubtitleItem(
                            start = timeToMs(timeRange[0]),
                            end = timeToMs(timeRange[1]),
                            text = lines.drop(2).joinToString("\n")
                        ))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    private fun timeToMs(time: String): Long {
        val parts = time.replace(",", ".").split(":")
        val h = parts[0].trim().toLong() * 3600000
        val m = parts[1].trim().toLong() * 60000
        val s = (parts[2].trim().toDouble() * 1000).toLong()
        return h + m + s
    }

    private fun saveProgress() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("last_time_ms", baseTimeMs.longValue)
            .putLong("last_offset_ms", timeOffsetMs.longValue)
            .apply()
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = Math.max(0, ms / 1000)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val hrs = mins / 60
        return if (hrs > 0) "%02d:%02d:%02d".format(hrs, mins % 60, secs) else "%02d:%02d".format(mins, secs)
    }

    private fun startMyForeground() {
        val chan = NotificationChannel("sub_chan", "Subtitles", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        startForeground(1, NotificationCompat.Builder(this, "sub_chan")
            .setContentTitle("Smart SRT Player Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build())
    }

    @Composable
    fun SeekButton(icon: ImageVector, isForward: Boolean, textCol: Color) {
        val scope = rememberCoroutineScope()
        var job by remember { mutableStateOf<Job?>(null) }

        Box(modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    job = scope.launch {
                        var speed = 1000L
                        var count = 0
                        while (isActive) {
                            if (isForward) baseTimeMs.longValue += speed else baseTimeMs.longValue -= speed
                            updateIndexByTime(baseTimeMs.longValue + timeOffsetMs.longValue)
                            count++
                            if (count > 5) speed = 10000L 
                            if (count > 15) speed = 60000L 
                            delay(100)
                        }
                    }
                    tryAwaitRelease()
                    job?.cancel()
                },
                onTap = {
                    val list = MainActivity.fullSubtitleList
                    val totalTime = baseTimeMs.longValue + timeOffsetMs.longValue
                    if (isForward) {
                        val next = list.firstOrNull { it.start > totalTime }
                        if (next != null) baseTimeMs.longValue = next.start - timeOffsetMs.longValue
                    } else {
                        val prev = list.lastOrNull { it.end < totalTime - 500 }
                        if (prev != null) baseTimeMs.longValue = prev.start - timeOffsetMs.longValue
                    }
                    updateIndexByTime(baseTimeMs.longValue + timeOffsetMs.longValue)
                }
            )
        }.padding(8.dp)) { Icon(icon, null, tint = textCol, modifier = Modifier.size(28.dp)) }
    }

    // فائل ایڈ کرنے کے لیے کسٹم بٹن (واضح نشانی کے ساتھ)
    @Composable
    fun FileAddButton(icon: ImageVector, label: String, activeColor: Color, textColor: Color, onClick: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = activeColor, modifier = Modifier.size(24.dp))
            Text(label, color = activeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }

    private fun showFloatingUI() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val fontPath = prefs.getString("last_font_path", null)
        val tSize = prefs.getFloat("text_size", 1.0f)
        val clockSize = prefs.getFloat("timer_size", 0.8f)
        val textCol = Color(prefs.getInt("text_color", Color.White.toArgb()))
        val bgCol = Color(prefs.getInt("bg_color", Color.Black.toArgb()))
        val opac = prefs.getFloat("opacity", 0.8f)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 400 }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SubtitleService)
            setViewTreeSavedStateRegistryOwner(this@SubtitleService)
            setContent {
                val font = if (fontPath != null && File(fontPath).exists()) FontFamily(Font(File(fontPath))) else FontFamily.Default
                val idx by currentIndex
                val active by isPlaying
                val offset by timeOffsetMs
                val baseTime by baseTimeMs
                val controlsFolded by isControlsFolded
                
                val displayTime = formatTime(baseTime + offset)
                val currentText = if (idx != -1 && MainActivity.fullSubtitleList.isNotEmpty()) 
                    MainActivity.fullSubtitleList[idx].text else ""

                val finalBgColor = if (isBgVisible.value) bgCol.copy(alpha = opac) else Color.Transparent

                Box(modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            params.x += dragAmount.x.toInt()
                            params.y += dragAmount.y.toInt()
                            windowManager.updateViewLayout(floatingView, params)
                        }
                    }
                    .background(finalBgColor, RoundedCornerShape(12.dp))
                    .padding(8.dp).widthIn(min = 250.dp, max = 500.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        
                        if (!controlsFolded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = displayTime, color = textCol.copy(alpha = 0.8f), fontSize = (18 * clockSize).sp, fontFamily = font)
                                if (offset != 0L) {
                                    Text(text = " (${if(offset>0) "+" else ""}${offset/1000}s)", color = Color.Yellow.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }

                        Text(
                            text = currentText, 
                            color = textCol, fontSize = (22 * tSize).sp, fontFamily = font,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickable { isControlsFolded.value = !isControlsFolded.value }
                                .padding(vertical = 4.dp).fillMaxWidth()
                        )
                        
                        if (!controlsFolded) {
                            // سنک اور سیک کنٹرولز
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { timeOffsetMs.longValue -= 1000; updateIndexByTime(baseTimeMs.longValue + timeOffsetMs.longValue) }) {
                                    Text("-1s", color = textCol)
                                }
                                SeekButton(Icons.Default.SkipPrevious, false, textCol)
                                FloatingActionButton(
                                    onClick = { 
                                        isPlaying.value = !isPlaying.value
                                        if(isPlaying.value) {
                                            lastSystemTime = SystemClock.elapsedRealtime()
                                            handler.post(ticker)
                                        } else {
                                            handler.removeCallbacks(ticker)
                                            lastSystemTime = 0L
                                        }
                                    }, 
                                    containerColor = Color(0xFFFFD700), shape = CircleShape, modifier = Modifier.size(40.dp)
                                ) { Icon(if(active) Icons.Default.Pause else Icons.Default.PlayArrow, "", tint = Color.Black) }
                                SeekButton(Icons.Default.SkipNext, true, textCol)
                                TextButton(onClick = { timeOffsetMs.longValue += 1000; updateIndexByTime(baseTimeMs.longValue + timeOffsetMs.longValue) }) {
                                    Text("+1s", color = textCol)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // فائل ایڈ کرنے کے بٹنز (واضح نشانیوں کے ساتھ)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                
                                // سب ٹائٹل ایڈ کرنے کا بٹن (سبز رنگ)
                                FileAddButton(Icons.Default.NoteAdd, "SRT", Color(0xFFC8E6C9), textCol) { openFilePicker("srt") }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                // فونٹ ایڈ کرنے کا بٹن (نیلا رنگ)
                                FileAddButton(Icons.Default.TextFields, "Font", Color(0xFFB3E5FC), textCol) { openFilePicker("font") }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                // باقی کنٹرولز
                                IconButton(onClick = { isBgVisible.value = !isBgVisible.value }) { 
                                    Icon(if(isBgVisible.value) Icons.Default.Visibility else Icons.Default.VisibilityOff, "", tint = textCol.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) 
                                }
                                IconButton(onClick = { stopSelf() }) { Icon(Icons.Default.Close, "", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                }
            }
        }
        windowManager.addView(floatingView, params)
    }

    private fun openFilePicker(type: String) {
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra("type", type)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        saveProgress()
        floatingView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        super.onDestroy()
    }
    override fun onBind(i: Intent?) = null
}
