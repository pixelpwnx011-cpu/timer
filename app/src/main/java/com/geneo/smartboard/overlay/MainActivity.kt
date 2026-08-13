package com.geneo.smartboard.overlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnToggleService: Button

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnToggleService = findViewById(R.id.btnToggleService)

        btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        btnToggleService.setOnClickListener { onToggleServiceClicked() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()

        // If overlay permission was granted while the user was in Settings and the
        // toolbox was previously enabled, auto (re)start the service.
        if (hasOverlayPermission() && Prefs.isOverlayEnabled(this) && !OverlayService.isRunning) {
            OverlayService.start(this)
            refreshStatus()
        }
    }

    private fun requestOverlayPermission() {
        if (hasOverlayPermission()) {
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
            refreshStatus()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun onToggleServiceClicked() {
        if (!hasOverlayPermission()) {
            Toast.makeText(
                this,
                "Please allow \"display over other apps\" first",
                Toast.LENGTH_LONG
            ).show()
            requestOverlayPermission()
            return
        }

        if (OverlayService.isRunning) {
            OverlayService.stop(this)
            Prefs.setOverlayEnabled(this, false)
        } else {
            OverlayService.start(this)
            Prefs.setOverlayEnabled(this, true)
        }
        refreshStatus()
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun refreshStatus() {
        if (hasOverlayPermission()) {
            tvOverlayStatus.text = "Status: Granted"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.geneo_success))
        } else {
            tvOverlayStatus.text = "Status: Not granted"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.geneo_warning))
        }

        if (OverlayService.isRunning) {
            tvServiceStatus.text = "Status: Running — bubble is on screen"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.geneo_success))
            btnToggleService.text = getString(R.string.stop_service)
        } else {
            tvServiceStatus.text = "Status: Stopped"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.geneo_warning))
            btnToggleService.text = getString(R.string.start_service)
        }
    }
}
