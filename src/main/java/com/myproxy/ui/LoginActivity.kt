package com.myproxy.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.myproxy.R
import com.myproxy.api.ApiResponse
import com.myproxy.api.MyProxyAPI
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var myProxyAPI: MyProxyAPI
    
    // UI Components
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusTextView: TextView
    private lateinit var carrierInfoTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        // Initialize API client
        myProxyAPI = MyProxyAPI(this)
        
        // Initialize UI components
        initializeViews()
        
        // Set up click listeners
        setupClickListeners()
        
        // Display carrier information
        displayCarrierInfo()
    }
    
    private fun initializeViews() {
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        progressBar = findViewById(R.id.progressBar)
        statusTextView = findViewById(R.id.statusTextView)
        carrierInfoTextView = findViewById(R.id.carrierInfoTextView)
        
        // Hide progress bar initially
        progressBar.visibility = View.GONE
    }
    
    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            performLogin()
        }
    }
    
    private fun displayCarrierInfo() {
        // Display current carrier and network information
        val carrierInfo = getCarrierInfo()
        carrierInfoTextView.text = carrierInfo
    }
    
    private fun getCarrierInfo(): String {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        
        val carrier = telephonyManager.networkOperatorName ?: "Unknown"
        val networkType = getNetworkType()
        val localIP = getLocalIP()
        
        return "Carrier: $carrier\nNetwork: $networkType\nLocal IP: $localIP"
    }
    
    private fun getNetworkType(): String {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        return when {
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                val telephonyManager = getSystemService(TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                when (telephonyManager.dataNetworkType) {
                    android.telephony.TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    android.telephony.TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                    android.telephony.TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    android.telephony.TelephonyManager.NETWORK_TYPE_GSM -> "2G"
                    else -> "Cellular"
                }
            }
            else -> "Unknown"
        }
    }
    
    private fun getLocalIP(): String {
        try {
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = java.util.Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        val hostAddress = address.hostAddress
                        if (hostAddress != null && !hostAddress.contains(":")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unknown"
    }
    
    private fun performLogin() {
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        
        if (username.isEmpty() || password.isEmpty()) {
            showToast("Please enter username and password")
            return
        }
        
        // Show loading state
        setLoadingState(true)
        updateStatus("Authenticating with carrier IP...")
        
        lifecycleScope.launch {
            try {
                // Perform login with carrier IP detection
                val result = myProxyAPI.loginMobileUser(username, password)
                
                when (result) {
                    is ApiResponse.Success -> {
                        val response = result.data
                        if (response.success) {
                            updateStatus("Login successful! Establishing tunnel...")
                            
                            // Establish tunnel connection
                            val tunnelResult = myProxyAPI.establishTunnel()
                            
                            when (tunnelResult) {
                                is ApiResponse.Success -> {
                                    val tunnelResponse = tunnelResult.data
                                    if (tunnelResponse.success) {
                                        updateStatus("Tunnel established successfully!")
                                        
                                        // Navigate to dashboard
                                        navigateToDashboard()
                                    } else {
                                        updateStatus("Failed to establish tunnel: ${tunnelResponse.message}")
                                        setLoadingState(false)
                                    }
                                }
                                is ApiResponse.Error -> {
                                    updateStatus("Tunnel error: ${tunnelResult.message}")
                                    setLoadingState(false)
                                }
                            }
                        } else {
                            updateStatus("Login failed: ${response.message}")
                            setLoadingState(false)
                        }
                    }
                    is ApiResponse.Error -> {
                        updateStatus("Login error: ${result.message}")
                        setLoadingState(false)
                    }
                }
                
            } catch (e: Exception) {
                updateStatus("Network error: ${e.message}")
                setLoadingState(false)
            }
        }
    }
    
    private fun setLoadingState(loading: Boolean) {
        runOnUiThread {
            if (loading) {
                progressBar.visibility = View.VISIBLE
                loginButton.isEnabled = false
                loginButton.text = "Connecting..."
            } else {
                progressBar.visibility = View.GONE
                loginButton.isEnabled = true
                loginButton.text = "Login"
            }
        }
    }
    
    private fun updateStatus(message: String) {
        runOnUiThread {
            statusTextView.text = message
        }
    }
    
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun navigateToDashboard() {
        runOnUiThread {
            val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
