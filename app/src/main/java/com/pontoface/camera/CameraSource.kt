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

interface CameraSource {
    fun startCamera(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView, onFrame: (Bitmap) -> Unit)
    fun stopCamera()
    fun isMock(): Boolean
}

@Singleton
class RealCameraSource @Inject constructor() : CameraSource {
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    override fun isMock() = false
    override fun startCamera(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView, onFrame: (Bitmap) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build().also { a -> a.setAnalyzer(cameraExecutor) { img -> onFrame(img.toBitmap()); img.close() } }
            try { provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analyzer) }
            catch (e: Exception) { Log.e("RealCamera", "Erro", e) }
        }, ContextCompat.getMainExecutor(context))
    }
    override fun stopCamera() { cameraExecutor.shutdown() }
}

@Singleton
class MockCameraSource @Inject constructor() : CameraSource {
    private var running = false
    override fun isMock() = true
    override fun startCamera(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView, onFrame: (Bitmap) -> Unit) {
        val path = BuildConfig.MOCK_IMAGE_PATH
        val bitmap = try { BitmapFactory.decodeFile(path) } catch(e: Exception) { null }
            ?: try { context.assets.open("mock_face.jpg").use { BitmapFactory.decodeStream(it) } } catch(e: Exception) { null }
            ?: return
        running = true
        Thread { while(running) { onFrame(bitmap); Thread.sleep(33) } }.start()
    }
    override fun stopCamera() { running = false }
}