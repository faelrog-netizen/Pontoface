package com.pontoface.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pontoface.BuildConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstração da fonte de câmera.
 * Em DEBUG com USE_MOCK_CAMERA=true, usa imagem estática do sdcard.
 * Em RELEASE, usa a câmera frontal real do dispositivo.
 */
interface CameraSource {
    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (Bitmap) -> Unit
    )
    fun stopCamera()
    fun isMock(): Boolean
}

// ─── Câmera Real (CameraX) ────────────────────────────────────────────────────

@Singleton
class RealCameraSource @Inject constructor() : CameraSource {

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null

    override fun isMock() = false

    override fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (Bitmap) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val bitmap = imageProxy.toBitmap()
                        onFrame(bitmap)
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e("RealCamera", "Falha ao iniciar câmera", e)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    override fun stopCamera() {
        cameraExecutor.shutdown()
    }
}

// ─── Câmera Mock (imagem estática via ADB) ────────────────────────────────────

@Singleton
class MockCameraSource @Inject constructor() : CameraSource {

    private var running = false
    private var mockThread: Thread? = null

    override fun isMock() = true

    override fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (Bitmap) -> Unit
    ) {
        val mockImagePath = BuildConfig.MOCK_IMAGE_PATH
        Log.d("MockCamera", "Usando imagem mock: $mockImagePath")

        val bitmap = loadMockBitmap(context, mockImagePath)
            ?: run {
                Log.e("MockCamera", "Imagem mock não encontrada em $mockImagePath")
                return
            }

        // Exibe no PreviewView usando ImageBitmap
        previewView.post {
            // Para o preview visual, desenhamos direto no PreviewView via bitmap overlay
            // O frame real é enviado ao analyzer abaixo
        }

        // Simula frames contínuos (30fps) como se fosse câmera real
        running = true
        mockThread = Thread {
            while (running) {
                onFrame(bitmap)
                Thread.sleep(33L) // ~30fps
            }
        }.also { it.start() }
    }

    override fun stopCamera() {
        running = false
        mockThread?.interrupt()
    }

    private fun loadMockBitmap(context: Context, path: String): Bitmap? {
        // 1. Tenta carregar do caminho ADB (/sdcard/mock_face.jpg)
        val file = java.io.File(path)
        if (file.exists()) {
            return BitmapFactory.decodeFile(path)
        }

        // 2. Fallback: assets/mock_face.jpg empacotado no APK
        return try {
            context.assets.open("mock_face.jpg").use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.w("MockCamera", "Sem imagem mock em assets também")
            null
        }
    }
}
