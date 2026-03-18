package com.smart.srtplayer // نئی آئی ڈی کے مطابق پیکج نام

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1A1A1A)) {
                    SRTPlayerMainScreen()
                }
            }
        }
    }
}

@Composable
fun SRTPlayerMainScreen() {
    val context = LocalContext.current
    var srtPreview by remember { mutableStateOf("کوئی فائل منتخب نہیں کی گئی") }
    var fontPath by remember { mutableStateOf<String?>(null) }

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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally) {
        
        Text("Smart SRT Player", fontSize = 28.sp, color = Color.Yellow, modifier = Modifier.padding(20.dp))

        Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.Black, RoundedCornerShape(12.dp)).padding(10.dp),
            contentAlignment = Alignment.Center) {
            Text(text = srtPreview, color = Color.White, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(30.dp))

        Button(onClick = { srtLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text("1. Select SRT File") }
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = { fontLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text("2. Select Font (.ttf)") }
        Spacer(modifier = Modifier.height(10.dp))
        
        Button(
            onClick = {
                if (checkOverlayPermission(context)) {
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
            Text("3. Launch Floating Player", fontSize = 18.sp)
        }
    }
}

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
