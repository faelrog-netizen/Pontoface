package com.pontoface.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pontoface.BuildConfig
import com.pontoface.R
import com.pontoface.data.TipoPonto
import com.pontoface.databinding.ActivityCameraBinding
import com.pontoface.face.FaceResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private val viewModel: CameraViewModel by viewModels()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) iniciarCamera()
        else showPermissionError()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Recebe dados da intent
        viewModel.funcionarioNome = intent.getStringExtra("funcionario") ?: "Funcionário"
        viewModel.tipoPonto = intent.getSerializableExtra("tipo") as? TipoPonto ?: TipoPonto.ENTRADA

        setupUI()
        observeState()
        checkCameraPermission()

        // Indicador de modo mock
        if (BuildConfig.USE_MOCK_CAMERA) {
            binding.mockBadge.visibility = View.VISIBLE
        }
    }

    private fun setupUI() {
        binding.tvNome.text = viewModel.funcionarioNome
        binding.tvTipo.text = "${viewModel.tipoPonto.emoji} ${viewModel.tipoPonto.label}"

        binding.btnRegistrar.setOnClickListener {
            viewModel.startScanning()
            binding.btnRegistrar.isEnabled = false
        }

        binding.btnVoltar.setOnClickListener { finish() }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED -> iniciarCamera()
            else -> requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun iniciarCamera() {
        viewModel.cameraSource.startCamera(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView
        ) { bitmap ->
            viewModel.processFrame(bitmap)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is CameraState.Idle -> {
                        binding.overlayView.setState(OverlayState.IDLE)
                        binding.tvStatus.text = "Pressione REGISTRAR para iniciar"
                        binding.progressBar.visibility = View.GONE
                        binding.btnRegistrar.isEnabled = true
                    }

                    is CameraState.Scanning -> {
                        binding.overlayView.setState(OverlayState.SCANNING)
                        binding.tvStatus.text = "Posicione seu rosto no oval"
                        binding.progressBar.visibility = View.VISIBLE
                        binding.progressBar.isIndeterminate = true
                    }

                    is CameraState.FaceDetected -> {
                        updateFaceUI(state.result)
                    }

                    is CameraState.Confirmed -> {
                        showSuccess(state)
                    }

                    is CameraState.Error -> {
                        binding.overlayView.setState(OverlayState.ERROR)
                        binding.tvStatus.text = state.message
                        binding.btnRegistrar.isEnabled = true
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentFace.collect { face ->
                face?.let { binding.overlayView.updateFace(it) }
            }
        }
    }

    private fun updateFaceUI(result: FaceResult) {
        binding.overlayView.setState(OverlayState.FACE_FOUND)

        val issues = mutableListOf<String>()
        if (!result.isLookingAtCamera) issues.add("Olhe para a câmera")
        if (!result.eyesOpen) issues.add("Abra os olhos")

        binding.tvStatus.text = if (issues.isEmpty()) {
            "✓ Rosto detectado — mantendo posição..."
        } else {
            issues.joinToString(" • ")
        }

        val confidencePct = (result.confidence * 100).toInt()
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = confidencePct
    }

    private fun showSuccess(state: CameraState.Confirmed) {
        binding.overlayView.setState(OverlayState.SUCCESS)
        binding.tvStatus.text = "✅ Ponto registrado! (${(state.confidence * 100).toInt()}%)"
        binding.progressBar.visibility = View.GONE

        // Vibração de sucesso
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 120), -1))

        // Fecha após 2s
        binding.root.postDelayed({ finish() }, 2000)
    }

    private fun showPermissionError() {
        binding.tvStatus.text = "Permissão de câmera necessária"
        binding.btnRegistrar.isEnabled = false
    }
}
