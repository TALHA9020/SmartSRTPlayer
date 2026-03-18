package com.smart.srtplayer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

data class SubtitleItem(val start: Long, val end: Long, val text: String)

class MainActivity : ComponentActivity() {
    
    companion object {
        var fullSubtitleList = listOf<SubtitleItem>()
        const val PREFS_NAME = "SRTPlayerPrefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    SRTPlayerMainScreen()
                }
            }
        }
    }
}

@Composable
fun SRTPlayerMainScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    
    // سٹیٹس (States) سیٹنگز کے لیے
    var srtPreview by remember { mutableStateOf("کوئی فائل منتخب نہیں کی گئی") }
    var fontPath by remember { mutableStateOf<String?>(null) }
    
    // سیٹنگز کی ڈیفالٹ ویلیوز لوڈ کریں
    var windowSize by remember { mutableStateOf(prefs.getFloat("window_size", 1.0f)) }
    var textColor by remember { mutableStateOf(Color(prefs.getInt("text_color", Color.White.toArgb()))) }
    var bgColor by remember { mutableStateOf(Color(prefs.getInt("bg_color", Color.Black.toArgb()))) }
    var opacity by remember { mutableStateOf(prefs.getFloat("opacity", 0.8f)) }

    // فائل لانچرز
    val srtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val rawText = readTextFromUri(context, it)
            MainActivity.fullSubtitleList = parseSrt(rawText)
            srtPreview = if (MainActivity.fullSubtitleList.isNotEmpty()) 
                MainActivity.fullSubtitleList[0].text else "فائل غلط ہے"
        }
    }

    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val file = copyFileToInternalStorage(context, it, "my_font.ttf")
            fontPath = file.absolutePath
            Toast.makeText(context, "فونٹ لوڈ ہو گیا!", Toast.LENGTH_SHORT).show()
        }
    }

    // سیٹنگز کو محفوظ کرنے کا فنکشن
    fun saveSettings() {
        prefs.edit().apply {
            putFloat("window_size", windowSize)
            putInt("text_color", textColor.toArgb())
            putInt("bg_color", bgColor.toArgb())
            putFloat("opacity", opacity)
            apply()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally) {
        
        Text("Smart SRT Player", fontSize = 28.sp, color = Color(0xFFFFD700), modifier = Modifier.padding(20.dp))

        // پریویو بکس
        val previewFont = if (fontPath != null) FontFamily(Font(File(fontPath))) else FontFamily.Default
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)
            .background(bgColor.copy(alpha = opacity), RoundedCornerShape(12.dp))
            .padding(10.dp),
            contentAlignment = Alignment.Center) {
            Text(text = srtPreview, color = textColor, fontSize = (20 * windowSize).sp, fontFamily = previewFont)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // فائل سلیکشن بٹنز
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { srtLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) { Text("1. Select SRT") }
            Spacer(modifier = Modifier.width(10.dp))
            Button(onClick = { fontLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) { Text("2. Select Font") }
        }

        Spacer(modifier = Modifier.height(25.dp))
        Divider(color = Color.Gray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(15.dp))

        // *** سیٹنگز کا نیا سیکشن ***
        Text("Player Settings", fontSize = 20.sp, color = Color.White, modifier = Modifier.align(Alignment.Start))
        
        // 1. ونڈو سائز سلائیڈر
        SettingSlider(label = "Window Size", value = windowSize, onValueChange = { windowSize = it; saveSettings() }, range = 0.5f..1.5f)
        
        // 2. اوپیسٹی سلائیڈر
        SettingSlider(label = "Background Opacity", value = opacity, onValueChange = { opacity = it; saveSettings() }, range = 0.0f..1.0f)

        // 3. کلر پکرز (سادہ دائرے)
        ColorPickerRow(label = "Text Color", selectedColor = textColor, onColorSelected = { textColor = it; saveSettings() },
            colors = listOf(Color.White, Color.Yellow, Color.Cyan, Color.Green))
        
        ColorPickerRow(label = "Background Color", selectedColor = bgColor, onColorSelected = { bgColor = it; saveSettings() },
            colors = listOf(Color.Black, Color.DarkGray, Color.Blue, Color(0xFF330000)))

        Spacer(modifier = Modifier.height(30.dp))
        
        // لانچ بٹن
        Button(
            onClick = {
                if (checkOverlayPermission(context)) {
                    saveSettings() // لانچ سے پہلے سیٹنگز سیو کریں
                    val intent = Intent(context, SubtitleService::class.java).apply {
                        putExtra("font_path", fontPath)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                    else context.startService(intent)
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00))
        ) {
            Text("3. Launch Floating Player", fontSize = 18.sp, color = Color.Black)
        }
    }
}

// *** ہیلپر کمپوزبلز سیٹنگز کے لیے ***

@Composable
fun SettingSlider(label: String, value: Float, onValueChange: (Float) -> Unit, range: ClosedRange<Float>) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.LightGray)
            Text("%.1f".format(value), color = Color.White)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD700), activeTrackColor = Color(0xFFFFD700)))
    }
}

@Composable
fun ColorPickerRow(label: String, selectedColor: Color, onColorSelected: (Color) -> Unit, colors: List<Color>) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.LightGray)
        Row {
            colors.forEach { color ->
                Box(modifier = Modifier.size(35.dp).padding(4.dp)
                    .background(color, CircleShape)
                    .clickable { onColorSelected(color) }
                    .then(if (color == selectedColor) Modifier.background(Color.White.copy(0.3f), CircleShape) else Modifier))
            }
        }
    }
}

// *** پرانے ہیلپر فنکشنز (بغیر تبدیلی کے) ***

fun readTextFromUri(context: Context, uri: Uri): String {
    val sb = StringBuilder()
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BufferedReader(InputStreamReader(stream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) sb.append(line).append("\n")
        }
    }
    return sb.toString()
}

fun parseSrt(content: String): List<SubtitleItem> {
    val list = mutableListOf<SubtitleItem>()
    val blocks = content.split(Regex("(\\r?\\n){2,}"))
    val timeRegex = Regex("(\\d{2}:\\d{2}:\\d{2},\\d{3}) --> (\\d{2}:\\d{2}:\\d{2},\\d{3})")
    
    for (block in blocks) {
        val lines = block.trim().lines()
        if (lines.size >= 3) {
            val match = timeRegex.find(lines[1])
            if (match != null) {
                list.add(SubtitleItem(
                    parseTimeToMs(match.groupValues[1]),
                    parseTimeToMs(match.groupValues[2]),
                    lines.drop(2).joinToString("\n")
                ))
            }
        }
    }
    return list
}

fun parseTimeToMs(time: String): Long {
    val p = time.replace(',', ':').split(":")
    return p[0].toLong()*3600000 + p[1].toLong()*60000 + p[2].toLong()*1000 + p[3].toLong()
}

fun copyFileToInternalStorage(context: Context, uri: Uri, name: String): File {
    val file = File(context.filesDir, name)
    context.contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }
    return file
}

fun checkOverlayPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        return false
    }
    return true
}
