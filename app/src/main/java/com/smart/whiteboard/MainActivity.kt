package com.smart.whiteboard

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt

data class DrawingLine(val path: Path, val color: Color, val strokeWidth: Float)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                WhiteboardApp()
            }
        }
    }
}

@Composable
fun WhiteboardApp() {
    val lines = remember { mutableStateListOf<DrawingLine>() }
    val undoneLines = remember { mutableStateListOf<DrawingLine>() }
    
    var currentColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableFloatStateOf(10f) }
    var isExpanded by remember { mutableStateOf(true) }
    var isPencilMode by remember { mutableStateOf(true) }
    var confirmRequired by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var panelOffset by remember { mutableStateOf(Offset(150f, 450f)) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.White)
        .onSizeChanged { screenSize = it }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, rotationChange ->
                scale = (scale * zoom).coerceIn(0.5f, 4f)
                rotation += rotationChange
                panelOffset += pan
            }
        }
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(isPencilMode, currentColor, strokeWidth) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val drawColor = if (isPencilMode) currentColor else Color.White
                        // یہاں اریزر کا سائز پنسل کے سائز سے 5 گنا کر دیا گیا ہے
                        val finalWidth = if (isPencilMode) strokeWidth else strokeWidth * 5f
                        lines.add(DrawingLine(Path().apply { moveTo(offset.x, offset.y) }, drawColor, finalWidth))
                        undoneLines.clear()
                    },
                    onDrag = { change, _ ->
                        if (change.pressed) {
                            change.consume()
                            lines.lastOrNull()?.path?.lineTo(change.position.x, change.position.y)
                            val last = lines.removeAt(lines.size - 1)
                            lines.add(last)
                        }
                    }
                )
            }
        ) {
            lines.forEach { line ->
                drawPath(path = line.path, color = line.color,
                    style = Stroke(width = line.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(panelOffset.x.roundToInt(), panelOffset.y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panelOffset += dragAmount
                    }
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = rotation)
        ) {
            ControlPanelContent(
                isExpanded = isExpanded,
                isPencilMode = isPencilMode,
                currentColor = currentColor,
                strokeWidth = strokeWidth,
                confirmRequired = confirmRequired,
                onModeChange = { isPencilMode = it },
                onColorChange = { currentColor = it; isPencilMode = true },
                onWidthChange = { strokeWidth = it },
                onUndo = { if (lines.isNotEmpty()) undoneLines.add(lines.removeAt(lines.size - 1)) },
                onRedo = { if (undoneLines.isNotEmpty()) lines.add(undoneLines.removeAt(undoneLines.size - 1)) },
                onClear = { if (confirmRequired) showDeleteDialog = true else lines.clear() },
                onFoldToggle = { isExpanded = !isExpanded },
                onConfirmToggle = { confirmRequired = it }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("تصدیق") },
                text = { Text("کیا آپ سارا بورڈ صاف کرنا چاہتے ہیں؟") },
                confirmButton = { TextButton(onClick = { lines.clear(); showDeleteDialog = false }) { Text("ہاں", color = Color.Red) } },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("نہیں") } }
            )
        }
    }
}

@Composable
fun ControlPanelContent(
    isExpanded: Boolean,
    isPencilMode: Boolean,
    currentColor: Color,
    strokeWidth: Float,
    confirmRequired: Boolean,
    onModeChange: (Boolean) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onFoldToggle: () -> Unit,
    onConfirmToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .background(Color(0xFF212121), RoundedCornerShape(30.dp))
            .border(1.5.dp, Color.Gray.copy(0.3f), RoundedCornerShape(30.dp))
            .padding(14.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isExpanded) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = { onModeChange(true) }, 
                    modifier = Modifier.background(if(isPencilMode) Color.Green else Color.Transparent, CircleShape)) {
                    Icon(Icons.Default.Edit, null, tint = if(isPencilMode) Color.Black else Color.White)
                }
                IconButton(onClick = { onModeChange(false) },
                    modifier = Modifier.background(if(!isPencilMode) Color.White else Color.Transparent, CircleShape)) {
                    Icon(Icons.Default.AutoFixNormal, null, tint = Color.Black)
                }
                val colors = listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Yellow)
                colors.forEach { color ->
                    Box(modifier = Modifier.size(24.dp).background(color, CircleShape)
                        .border(if (currentColor == color) 2.dp else 0.dp, Color.White, CircleShape)
                        .clickable { onColorChange(color) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Divider(color = Color.DarkGray)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onClear) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                Checkbox(checked = confirmRequired, onCheckedChange = onConfirmToggle, colors = CheckboxDefaults.colors(uncheckedColor = Color.Gray))
                Slider(value = strokeWidth, onValueChange = onWidthChange, valueRange = 4f..120f, modifier = Modifier.width(70.dp))
                // یہاں بھی پریویو میں اریزر کا سائز 5 گنا بڑا نظر آئے گا
                Box(modifier = Modifier.size(((if(isPencilMode) strokeWidth else strokeWidth * 5f)/6).coerceIn(3f, 25f).dp).background(if(isPencilMode) currentColor else Color.LightGray, CircleShape))
                IconButton(onClick = onUndo) { Icon(Icons.Default.Undo, null, tint = Color.White) }
                IconButton(onClick = onRedo) { Icon(Icons.Default.Redo, null, tint = Color.White) }
                IconButton(onClick = { onFoldToggle() }) { Icon(Icons.Default.ExpandLess, null, tint = Color.Cyan) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Box(Modifier.size(45.dp, 22.dp).background(if(isPencilMode) Color.Green else Color.LightGray, CircleShape).clickable { onModeChange(!isPencilMode) })
                Box(Modifier.size(45.dp, 22.dp).background(Color.Red, CircleShape).clickable { onClear() })
                Box(Modifier.size(45.dp, 22.dp).background(Color.Blue, CircleShape).clickable { onFoldToggle() })
            }
        }
    }
}
