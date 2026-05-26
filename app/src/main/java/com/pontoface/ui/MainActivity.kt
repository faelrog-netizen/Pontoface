package com.pontoface.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pontoface.data.TipoPonto
import com.pontoface.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClock()
        setupButtons()
    }

    private fun setupClock() {
        val runnable = object : Runnable {
            override fun run() {
                val now = LocalDateTime.now()
                binding.tvHora.text = now.format(DateTimeFormatter.ofPattern("HH:mm"))
                binding.tvData.text = now.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM"))
                binding.root.postDelayed(this, 1000)
            }
        }
        binding.root.post(runnable)
    }

    private fun setupButtons() {
        val nome = "João Silva" // Em produção: pegar do login

        binding.btnEntrada.setOnClickListener  { abrirCamera(nome, TipoPonto.ENTRADA) }
        binding.btnSaida.setOnClickListener    { abrirCamera(nome, TipoPonto.SAIDA) }
        binding.btnPausa.setOnClickListener    { abrirCamera(nome, TipoPonto.PAUSA) }
        binding.btnRetorno.setOnClickListener  { abrirCamera(nome, TipoPonto.RETORNO) }

        binding.btnHistorico.setOnClickListener {
            startActivity(Intent(this, HistoricoActivity::class.java))
        }
    }

    private fun abrirCamera(nome: String, tipo: TipoPonto) {
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra("funcionario", nome)
            putExtra("tipo", tipo)
        }
        startActivity(intent)
    }
}
