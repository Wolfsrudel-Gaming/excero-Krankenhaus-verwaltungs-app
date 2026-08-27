package de.excero.tvwartung.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged

/**
 * Zustand einer handschriftlichen Unterschrift: die einzelnen Striche als
 * Punktlisten. Kann als Bitmap für den PDF-Export gerastert werden.
 */
class SignatureState {
    val strokes = mutableStateListOf<SnapshotStateList<Offset>>()
    var width = mutableIntStateOf(0)
    var height = mutableIntStateOf(0)

    fun startStroke(o: Offset) {
        strokes.add(mutableStateListOf(o))
    }

    fun addPoint(o: Offset) {
        strokes.lastOrNull()?.add(o)
    }

    fun clear() = strokes.clear()

    fun hasContent(): Boolean = strokes.any { it.size > 1 }

    /** Rastert die Unterschrift auf transparentem Hintergrund. */
    fun toBitmap(): Bitmap? {
        val w = width.intValue
        val h = height.intValue
        if (!hasContent() || w <= 0 || h <= 0) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val paint = AndroidPaint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 4f
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            isAntiAlias = true
        }
        strokes.forEach { pts ->
            if (pts.size > 1) {
                val path = AndroidPath()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                canvas.drawPath(path, paint)
            }
        }
        return bmp
    }
}

/** Zeichenfläche für eine Unterschrift. */
@Composable
fun SignaturePad(state: SignatureState, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .onSizeChanged {
                state.width.intValue = it.width
                state.height.intValue = it.height
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { state.startStroke(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        state.addPoint(change.position)
                    }
                )
            }
    ) {
        state.strokes.forEach { pts ->
            if (pts.size > 1) {
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
