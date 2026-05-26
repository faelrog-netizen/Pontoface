package com.pontoface.face

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class FaceResult(
    val detected: Boolean,
    val confidence: Float,       // 0.0 - 1.0
    val boundingBox: Rect?,
    val isLookingAtCamera: Boolean,
    val eyesOpen: Boolean,
    val snapshot: Bitmap?        // frame capturado no momento do reconhecimento
)

@Singleton
class FaceDetector @Inject constructor() {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.20f) // rosto deve ocupar pelo menos 20% da imagem
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(options)

    /**
     * Analisa um frame e retorna FaceResult.
     * Suspende até o ML Kit responder.
     */
    suspend fun analyze(bitmap: Bitmap): FaceResult =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    val result = if (faces.isEmpty()) {
                        FaceResult(
                            detected = false,
                            confidence = 0f,
                            boundingBox = null,
                            isLookingAtCamera = false,
                            eyesOpen = false,
                            snapshot = null
                        )
                    } else {
                        val face = faces.maxByOrNull { it.boundingBox.width() }!! // maior rosto
                        buildResult(face, bitmap)
                    }
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { e ->
                    Log.e("FaceDetector", "Erro na detecção", e)
                    if (cont.isActive) cont.resume(
                        FaceResult(false, 0f, null, false, false, null)
                    )
                }
        }

    private fun buildResult(face: Face, bitmap: Bitmap): FaceResult {
        val leftEye  = face.leftEyeOpenProbability ?: 0f
        val rightEye = face.rightEyeOpenProbability ?: 0f
        val eyesOpen = leftEye > 0.6f && rightEye > 0.6f

        // Considera "olhando para câmera" se rotação Y < 15 graus
        val headYaw = face.headEulerAngleY
        val headPitch = face.headEulerAngleX
        val isLooking = Math.abs(headYaw) < 15f && Math.abs(headPitch) < 15f

        // Confiança baseada em qualidade do bounding box e posição
        val faceArea = face.boundingBox.width() * face.boundingBox.height()
        val imageArea = bitmap.width * bitmap.height
        val sizeScore = minOf(faceArea.toFloat() / imageArea * 10f, 1f)
        val poseScore = if (isLooking) 1f else 0.5f
        val eyeScore  = if (eyesOpen) 1f else 0.4f
        val confidence = (sizeScore * 0.4f + poseScore * 0.3f + eyeScore * 0.3f)

        // Recorta snapshot do rosto
        val snapshot = cropFace(bitmap, face.boundingBox)

        return FaceResult(
            detected = true,
            confidence = confidence,
            boundingBox = face.boundingBox,
            isLookingAtCamera = isLooking,
            eyesOpen = eyesOpen,
            snapshot = snapshot
        )
    }

    private fun cropFace(bitmap: Bitmap, box: Rect): Bitmap? {
        return try {
            val padding = (box.width() * 0.3f).toInt()
            val left   = maxOf(0, box.left - padding)
            val top    = maxOf(0, box.top - padding)
            val right  = minOf(bitmap.width, box.right + padding)
            val bottom = minOf(bitmap.height, box.bottom + padding)
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (e: Exception) { null }
    }
}
