package com.example.shiftsnap

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.shiftsnap.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupUser()
        setupClickListeners()
    }

    // Inflate menu via onCreateOptionsMenu (cara yang benar)
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // Handle klik menu item
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_dark_mode -> {
                val newMode = !session.isDarkMode()
                session.setDarkMode(newMode)
                AppCompatDelegate.setDefaultNightMode(
                    if (newMode) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                val msg = if (newMode) "Mode Gelap aktif" else "Mode Terang aktif"
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                true
            }
            R.id.menu_logout -> {
                confirmLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Yakin ingin keluar dari akun ini?")
            .setPositiveButton("Logout") { _, _ ->
                session.logout()
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupUser() {
        val operatorId = session.getOperatorId()
        binding.tvOperatorName.text = if (operatorId.isNotEmpty()) operatorId else "Operator"
    }

    private fun setupClickListeners() {
        binding.cardNewReport.setOnClickListener { navigateToNewReport() }
        binding.cardRecap.setOnClickListener { navigateToRecap() }
        binding.fabNewReport.setOnClickListener { navigateToNewReport() }
    }

    private fun navigateToNewReport() {
        startActivity(Intent(this, FormReportActivity::class.java))
    }

    private fun navigateToRecap() {
        startActivity(Intent(this, RecapListActivity::class.java))
    }
}
