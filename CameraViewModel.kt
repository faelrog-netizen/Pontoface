package com.pontoface.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pontoface.camera.CameraSource
import com.pontoface.data.PontoRepository
import com.pontoface.data.TipoPonto
import com.pontoface.face.FaceDetector
import com.pontoface.face.FaceResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

sealed class CameraState {
    object Idle : CameraState()
    object Scanning : CameraState()
    data class FaceDetected(val result: FaceResult) : CameraState()
    data class Confirmed(val confidence: Float, val tipo: TipoPonto) : CameraState()
    data class Error(val message: String) : CameraState()
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val cameraSource: CameraSource,
    private val faceDetector: FaceDetector,
    private val repository: PontoRepository
) : ViewModel() {

    private val _state = MutableStateFlow<CameraState>(CameraState.Idle)
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private val _currentFace = MutableStateFlow<FaceResult?>(null)
    val currentFace: StateFlow<FaceResult?> = _currentFace.asStateFlow()

    private var isAnalyzing = false
    private var frameCount = 0
    private var consecutiveDetections = 0
    private val REQUIRED_CONSECUTIVE = 10 // frames consecutivos com rosto válido

    var funcionarioNome: String = "Funcionário"
    var tipoPonto: TipoPonto = TipoPonto.ENTRADA

    fun startScanning() {
        _state.value = CameraState.Scanning
        consecutiveDetections = 0
    }

    /**
     * Chamado a cada frame da câmera (real ou mock).
     * Processa 1 de cada 5 frames para economizar CPU.
     */
    fun processFrame(bitmap: Bitmap) {
        frameCount++
        if (frameCount % 5 != 0) return
        if (isAnalyzing || _state.value is CameraState.Confirmed) return

        isAnalyzing = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = faceDetector.analyze(bitmap)
                _currentFace.value = result

                if (_state.value == CameraState.Scanning) {
                    if (result.detected && result.isLookingAtCamera && result.eyesOpen) {
                        consecutiveDetections++
                        _state.value = CameraState.FaceDetected(result)

                        if (consecutiveDetections >= REQUIRED_CONSECUTIVE) {
                            confirmPonto(result)
                        }
                    } else {
                        consecutiveDetections = 0
                        if (result.detected) {
                            _state.value = CameraState.FaceDetected(result)
                        } else {
                            _state.value = CameraState.Scanning
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraVM", "Erro ao processar frame", e)
            } finally {
                isAnalyzing = false
            }
        }
    }

    private suspend fun confirmPonto(faceResult: FaceResult) {
        val fotoPath = saveFaceSnapshot(faceResult.snapshot)

        repository.registrarPonto(
            nome = funcionarioNome,
            tipo = tipoPonto,
            confidence = faceResult.confidence,
            fotoPath = fotoPath
        )

        _state.value = CameraState.Confirmed(faceResult.confidence, tipoPonto)
    }

    private fun saveFaceSnapshot(bitmap: Bitmap?): String? {
        if (bitmap == null) return null
        return try {
            val dir = File(context.filesDir, "snapshots").also { it.mkdirs() }
            val file = File(dir, "face_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("CameraVM", "Erro ao salvar snapshot", e)
            null
        }
    }

    fun reset() {
        consecutiveDetections = 0
        _state.value = CameraState.Idle
        _currentFace.value = null
    }

    override fun onCleared() {
        super.onCleared()
        cameraSource.stopCamera()
    }
}
