package com.example.shiftsnap

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.shiftsnap.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.Snackbar

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = SessionManager(this)

        // Terapkan dark/light mode sebelum setContentView
        AppCompatDelegate.setDefaultNightMode(
            if (session.isDarkMode()) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Jika sudah login, langsung ke MainActivity
        if (session.isLoggedIn()) {
            goToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUi()
    }

    private fun setupUi() {
        // Tombol Masuk via keyboard "Done"
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val id = binding.etOperatorId.text?.toString()?.trim() ?: ""
        val pass = binding.etPassword.text?.toString() ?: ""

        // Reset error state
        binding.tilOperatorId.error = null
        binding.tilPassword.error = null

        if (id.isEmpty()) {
            binding.tilOperatorId.error = "ID Operator wajib diisi"
            binding.tilOperatorId.requestFocus()
            return
        }
        if (pass.isEmpty()) {
            binding.tilPassword.error = "Password wajib diisi"
            binding.tilPassword.requestFocus()
            return
        }

        // Validasi dummy credentials
        val expectedPass = SessionManager.DUMMY_USERS[id]
        if (expectedPass == null || expectedPass != pass) {
            Snackbar.make(
                binding.root,
                getString(R.string.error_invalid_credentials),
                Snackbar.LENGTH_SHORT
            ).show()
            binding.tilPassword.error = " " // beri warna merah tanpa teks duplikat
            return
        }

        // Login sukses
        session.saveLogin(id)
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
