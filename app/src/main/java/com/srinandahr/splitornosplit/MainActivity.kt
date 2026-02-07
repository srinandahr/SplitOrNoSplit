package com.srinandahr.splitornosplit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    // Store the fetched groups here
    private var availableGroups = mutableListOf<Group>()
    private val PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestPermissions()

        // UI Elements
        val etApiKey = findViewById<EditText>(R.id.et_api_key)
        val btnFetch = findViewById<Button>(R.id.btn_fetch_groups)
        val spinnerGroups = findViewById<Spinner>(R.id.spinner_groups)
        val btnSave = findViewById<Button>(R.id.btn_save_config)
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        val btnPause = findViewById<Button>(R.id.btn_pause) // The new Button
        val btnReset = findViewById<Button>(R.id.btn_reset)

        val sharedPref = getSharedPreferences("SplitAppPrefs", Context.MODE_PRIVATE)

        // 1. LOAD SAVED DATA
        val savedKey = sharedPref.getString("API_KEY", "")
        val savedGroupId = sharedPref.getLong("GROUP_ID", 0L)
        val savedGroupName = sharedPref.getString("GROUP_NAME", "None")

        etApiKey.setText(savedKey?.replace("Bearer ", ""))

        if (savedGroupId != 0L) {
            tvStatus.text = "Active: Posting to '$savedGroupName'"
            tvStatus.setTextColor(Color.parseColor("#388E3C")) // Dark Green

            // Add saved group to list so spinner isn't empty
            val savedList = listOf(savedGroupName ?: "Unknown")
            val adapter = ArrayAdapter(this, R.layout.item_spinner, savedList)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerGroups.adapter = adapter
        }

        // 2. FETCH BUTTON LOGIC
        btnFetch.setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Enter API Key first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val finalKey = if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"
            Toast.makeText(this, "Fetching Groups...", Toast.LENGTH_SHORT).show()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = SplitwiseNetwork.api.getGroups(finalKey)
                    withContext(Dispatchers.Main) {
                        availableGroups.clear()
                        availableGroups.addAll(response.groups)

                        val groupNames = availableGroups.map { it.name }
                        val adapter = ArrayAdapter(this@MainActivity, R.layout.item_spinner, groupNames)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerGroups.adapter = adapter

                        Toast.makeText(this@MainActivity, "Found ${availableGroups.size} groups!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // 3. PAUSE BUTTON LOGIC
        // Helper function to update UI based on state
        fun updatePauseButtonUI(isPaused: Boolean) {
            if (isPaused) {
                btnPause.text = "Resume App"
                btnPause.setBackgroundColor(Color.parseColor("#F57C00")) // Orange
                tvStatus.text = "App is Paused"
                tvStatus.setTextColor(Color.parseColor("#F57C00"))
            } else {
                btnPause.text = "Pause App"
                btnPause.setBackgroundColor(Color.parseColor("#2196F3")) // Blue

                // Restore status text
                val groupName = sharedPref.getString("GROUP_NAME", "")
                if (groupName!!.isNotEmpty()) {
                    tvStatus.text = "Active: Posting to '$groupName'"
                    tvStatus.setTextColor(Color.parseColor("#388E3C")) // Green
                } else {
                    tvStatus.text = "Not Configured"
                    tvStatus.setTextColor(Color.GRAY)
                }
            }
        }

        // Initialize State
        var isPaused = sharedPref.getBoolean("isPaused", false)
        updatePauseButtonUI(isPaused)

        // Click Listener
        btnPause.setOnClickListener {
            isPaused = !isPaused
            sharedPref.edit().putBoolean("isPaused", isPaused).apply()
            updatePauseButtonUI(isPaused)
            Toast.makeText(this, if(isPaused) "App Paused" else "App Resumed", Toast.LENGTH_SHORT).show()
        }

        // 4. SAVE BUTTON LOGIC
        btnSave.setOnClickListener {
            val selectedPosition = spinnerGroups.selectedItemPosition

            // Validation: Make sure they actually fetched and picked something
            if (availableGroups.isEmpty()) {
                Toast.makeText(this, "Please Fetch Groups first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedPosition == -1) {
                Toast.makeText(this, "Please select a group", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Get the selected group object using the position
            val selectedGroup = availableGroups[selectedPosition]

            val apiKey = etApiKey.text.toString().trim()
            val finalKey = if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"

            with(sharedPref.edit()) {
                putString("API_KEY", finalKey)
                putLong("GROUP_ID", selectedGroup.id)
                putString("GROUP_NAME", selectedGroup.name)
                apply()
            }

            tvStatus.text = "Active: Posting to '${selectedGroup.name}'"
            tvStatus.setTextColor(Color.parseColor("#388E3C"))
            Toast.makeText(this, "Configuration Saved", Toast.LENGTH_SHORT).show()

            // If we were paused, this update doesn't auto-resume, but we ensure UI is consistent
            if (!isPaused) updatePauseButtonUI(false)
        }

        // 5. RESET BUTTON LOGIC
        btnReset.setOnClickListener {
            sharedPref.edit().clear().apply()
            etApiKey.text.clear()
            spinnerGroups.adapter = null
            availableGroups.clear()

            tvStatus.text = "Status: Not Configured"
            tvStatus.setTextColor(Color.RED)

            // Reset pause state to default (False)
            isPaused = false
            updatePauseButtonUI(false)

            Toast.makeText(this, "App Reset Successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
}