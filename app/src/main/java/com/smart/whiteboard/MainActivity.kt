package com.smart.whiteboard

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

// ڈیٹا ماڈل
data class SubtitleItem(val start: Long, val end: Long, val text: String)

class MainActivity : ComponentActivity() {
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
    var srtContent by remember { mutableStateOf("کوئی فائل منتخب نہیں کی گئی") }
    var fontPath by remember { mutableStateOf<String?>(null) }
    var subtitleList by remember { mutableStateOf(listOf<SubtitleItem>()) }

    // فونٹ لوڈ کرنے کا سسٹم
    val customFontFamily = remember(fontPath) {
        if (fontPath != null) FontFamily(Font(File(fontPath!!))) else FontFamily.Default
    }

    // SRT فائل پک کرنے والا لانچر
    val srtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val rawText = readTextFromUri(context, it)
            subtitleList = parseSrt(rawText)
            srtContent = if (subtitleList.isNotEmpty()) subtitleList[0].text else "فائل خالی ہے یا فارمیٹ غلط ہے"
        }
    }

    // فونٹ فائل (.ttf) پک کرنے والا لانچر
    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val file = copyFileToInternalStorage(context, it, "custom_font.ttf")
            fontPath = file.absolutePath
            Toast.makeText(context, "فونٹ اپلائی ہو گیا!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Smart SRT Player", fontSize = 30.sp, color = Color.Yellow, modifier = Modifier.padding(20.dp))

        // ڈسپلے باکس (پریویو)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color.Black, RoundedCornerShape(12.dp))
                .padding(15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = srtContent,
                color = Color.White,
                fontSize = 22.sp,
                fontFamily = customFontFamily,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        // کنٹرول بٹنز
        Button(
            onClick = { srtLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Text("1. منتخب کریں SRT فائل")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { fontLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
        ) {
            Text("2. جمیل نوری فونٹ منتخب کریں (.ttf)")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                if (checkOverlayPermission(context)) {
                    // یہاں سروس اسٹارٹ کرنے کا کوڈ آئے گا
                    Toast.makeText(context, "فلوٹنگ ونڈو تیار ہے!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00))
        ) {
            Text("3. لانچ فلوٹنگ پلیئر")
        }
    }
}

// مددگار فنکشنز (Helper Functions)

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
                list.add(SubtitleItem(0L, 0L, lines.drop(2).joinToString("\n")))
            }
        }
    }
    return list
}

fun copyFileToInternalStorage(context: Context, uri: Uri, newName: String): File {
    val file = File(context.filesDir, newName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output ->
            input.copyTo(output)
        }
    }
    return file
}

fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
            false
        } else true
    } else true
}
