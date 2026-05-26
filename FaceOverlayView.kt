package com.pontoface.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.pontoface.face.FaceResult

enum class OverlayState { IDLE, SCANNING, FACE_FOUND, SUCCESS, ERROR }

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var state = OverlayState.IDLE
    private var faceResult: FaceResult? = null

    // Oval guide
    private val ovalRect = RectF()
    private val ovalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Face bounding box
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.CYAN
    }

    // Corner brackets
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    // Scrim (dark overlay outside oval)
    private val scrimPaint = Paint().apply {
        color = Color.argb(140, 0, 0, 0)
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Scanning animation
    private var scanY = 0f
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
    }

    private val animRunnable = object : Runnable {
        override fun run() {
            scanY += 4f
            if (scanY > ovalRect.bottom) scanY = ovalRect.top
            invalidate()
            if (state == OverlayState.SCANNING || state == OverlayState.FACE_FOUND) {
                postDelayed(this, 16)
            }
        }
    }

    fun setState(newState: OverlayState) {
        state = newState
        removeCallbacks(animRunnable)

        ovalPaint.color = when (state) {
            OverlayState.IDLE      -> Color.argb(180, 255, 255, 255)
            OverlayState.SCANNING  -> Color.argb(255, 0, 200, 255)
            OverlayState.FACE_FOUND -> Color.argb(255, 100, 220, 100)
            OverlayState.SUCCESS   -> Color.argb(255, 0, 255, 100)
            OverlayState.ERROR     -> Color.argb(255, 255, 80, 80)
        }

        cornerPaint.color = ovalPaint.color

        if (state == OverlayState.SCANNING || state == OverlayState.FACE_FOUND) {
            scanPaint.color = ovalPaint.color
            scanPaint.shader = LinearGradient(
                0f, scanY - 40f, 0f, scanY + 40f,
                intArrayOf(Color.TRANSPARENT, ovalPaint.color, Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            post(animRunnable)
        }

        invalidate()
    }

    fun updateFace(result: FaceResult) {
        faceResult = result
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h * 0.42f
        val rx = w * 0.36f
        val ry = h * 0.28f
        ovalRect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        scanY = ovalRect.top
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Scrim layer com buraco oval
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(bmp)
        bmpCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        bmpCanvas.drawOval(ovalRect, clearPaint)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        bmp.recycle()

        // Oval border
        canvas.drawOval(ovalRect, ovalPaint)

        // Corner brackets
        drawCornerBrackets(canvas)

        // Scan line (quando ativo)
        if (state == OverlayState.SCANNING || state == OverlayState.FACE_FOUND) {
            if (scanY in ovalRect.top..ovalRect.bottom) {
                scanPaint.shader = LinearGradient(
                    0f, scanY - 30f, 0f, scanY + 30f,
                    intArrayOf(Color.TRANSPARENT, ovalPaint.color, Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawLine(ovalRect.left, scanY, ovalRect.right, scanY, scanPaint)
            }
        }

        // Face bounding box (se detectado)
        faceResult?.boundingBox?.let { box ->
            if (faceResult?.detected == true) {
                val scaleX = width.toFloat() / 480f  // normalize para resolução comum
                val scaleY = height.toFloat() / 640f
                val scaledBox = RectF(
                    box.left * scaleX,
                    box.top * scaleY,
                    box.right * scaleX,
                    box.bottom * scaleY
                )
                canvas.drawRoundRect(scaledBox, 8f, 8f, boxPaint)
            }
        }

        // Success checkmark
        if (state == OverlayState.SUCCESS) {
            drawCheckmark(canvas)
        }
    }

    private fun drawCornerBrackets(canvas: Canvas) {
        val len = 28f
        val r = ovalRect
        val pad = 8f

        // Top-left
        canvas.drawLine(r.left - pad, r.centerY() - len, r.left - pad, r.centerY(), cornerPaint)
        // Top-right
        canvas.drawLine(r.right + pad, r.centerY() - len, r.right + pad, r.centerY(), cornerPaint)

        // Top arc endpoints
        canvas.drawLine(r.centerX() - len, r.top - pad, r.centerX(), r.top - pad, cornerPaint)
        canvas.drawLine(r.centerX(), r.top - pad, r.centerX() + len, r.top - pad, cornerPaint)
        // Bottom
        canvas.drawLine(r.centerX() - len, r.bottom + pad, r.centerX() + len, r.bottom + pad, cornerPaint)
    }

    private fun drawCheckmark(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 0, 255, 100)
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val cx = ovalRect.centerX()
        val cy = ovalRect.centerY()
        val path = Path().apply {
            moveTo(cx - 30f, cy)
            lineTo(cx - 5f, cy + 25f)
            lineTo(cx + 35f, cy - 25f)
        }
        canvas.drawPath(path, paint)
    }
}
