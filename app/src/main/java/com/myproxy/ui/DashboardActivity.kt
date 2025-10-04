package com.myproxy.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.myproxy.R
import com.myproxy.api.ApiResponse
import com.myproxy.api.MyProxyAPI
import com.myproxy.api.ProxyConfig
import com.myproxy.vpn.ProxyServerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {
    
    private lateinit var myProxyAPI: MyProxyAPI
    private var proxyService: ProxyServerService? = null
    private var isBound = false
    
    // UI Components
    private lateinit var userInfoTextView: TextView
    private lateinit var carrierInfoTextView: TextView
    private lateinit var proxyStatusTextView: TextView
    private lateinit var connectionStatusTextView: TextView
    private lateinit var proxyConfigTextView: TextView
    private lateinit var activeConnectionsTextView: TextView
    
    private lateinit var startProxyButton: Button
    private lateinit var stopProxyButton: Button
    private lateinit var refreshStatusButton: Button
    private lateinit var logoutButton: Button
    
    private lateinit var progressBar: ProgressBar
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ProxyServerService.ProxyServerBinder
            proxyService = binder.getService()
            isBound = true
            
            // Set up status listener
            proxyService?.setStatusListener { isRunning, message ->
                runOnUiThread {
                    updateProxyStatus(isRunning, message)
                }
            }
            
            // Update UI with current status
            updateProxyStatusUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            proxyService = null
            isBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        
        // Initialize API client
        myProxyAPI = MyProxyAPI(this)
        
        // Check if user is authenticated
        if (!myProxyAPI.isAuthenticated()) {
            startLogin()
            return
        }
        
        // Initialize UI components
        initializeViews()
        
        // Set up click listeners
        setupClickListeners()
        
        // Bind to proxy service
        bindProxyService()
        
        // Display user information
        displayUserInfo()
        
        // Start status monitoring
        startStatusMonitoring()
    }
    
    private fun initializeViews() {
        userInfoTextView = findViewById(R.id.userInfoTextView)
        carrierInfoTextView = findViewById(R.id.carrierInfoTextView)
        proxyStatusTextView = findViewById(R.id.proxyStatusTextView)
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)
        proxyConfigTextView = findViewById(R.id.proxyConfigTextView)
        activeConnectionsTextView = findViewById(R.id.activeConnectionsTextView)
        
        startProxyButton = findViewById(R.id.startProxyButton)
        stopProxyButton = findViewById(R.id.stopProxyButton)
        refreshStatusButton = findViewById(R.id.refreshStatusButton)
        logoutButton = findViewById(R.id.logoutButton)
        
        progressBar = findViewById(R.id.progressBar)
        
        // Initially hide progress bar
        progressBar.visibility = View.GONE
    }
    
    private fun setupClickListeners() {
        startProxyButton.setOnClickListener {
            startLocalProxyServer()
        }
        
        stopProxyButton.setOnClickListener {
            stopLocalProxyServer()
        }
        
        refreshStatusButton.setOnClickListener {
            refreshConnectionStatus()
        }
        
        logoutButton.setOnClickListener {
            performLogout()
        }
    }
    
    private fun bindProxyService() {
        val intent = Intent(this, ProxyServerService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun displayUserInfo() {
        val user = myProxyAPI.currentUser
        val serverConfig = myProxyAPI.serverConfig
        
        if (user != null && serverConfig != null) {
            val userInfo = """
                User: ${user.mobileName}
                Username: ${user.username}
                Status: ${user.status}
                Subscription: ${user.subscriptionExpiry}
                
                Server: ${serverConfig.proxyHost}
                HTTP Port: ${serverConfig.httpPort}
                SOCKS Port: ${serverConfig.socksPort}
            """.trimIndent()
            
            userInfoTextView.text = userInfo
        }
        
        // Display carrier information
        displayCarrierInfo()
    }
    
    private fun displayCarrierInfo() {
        val carrierInfo = getCarrierInfo()
        carrierInfoTextView.text = carrierInfo
    }
    
    private fun getCarrierInfo(): String {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        val carrier = telephonyManager.networkOperatorName ?: "Unknown"
        val networkType = getNetworkType()
        val localIP = getLocalIP()
        
        return """
            📱 Device Information
            Carrier: $carrier
            Network: $networkType
            Local IP: $localIP
            Device ID: ${getDeviceId()}
        """.trimIndent()
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
    
    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
    }
    
    private fun startLocalProxyServer() {
        val user = myProxyAPI.currentUser
        val serverConfig = myProxyAPI.serverConfig
        
        if (user == null || serverConfig == null) {
            showToast("User not authenticated")
            return
        }
        
        // Create proxy configuration
        val proxyConfig = ProxyConfig(
            host = serverConfig.proxyHost,
            httpPort = serverConfig.httpPort,
            socksPort = serverConfig.socksPort,
            username = user.username,
            password = "", // You'll need to get the password from login
            carrierIP = getLocalIP(),
            isActive = true
        )
        
        // Start proxy service
        proxyService?.startProxyServer(proxyConfig)
        
        // Update UI
        updateProxyConfigDisplay(proxyConfig)
    }
    
    private fun stopLocalProxyServer() {
        proxyService?.stopProxyServer()
    }
    
    private fun updateProxyStatus(isRunning: Boolean, message: String) {
        val status = if (isRunning) {
            "🟢 Proxy Server: ACTIVE\n$message"
        } else {
            "🔴 Proxy Server: INACTIVE\n$message"
        }
        
        proxyStatusTextView.text = status
        
        // Update button states
        startProxyButton.isEnabled = !isRunning
        stopProxyButton.isEnabled = isRunning
        
        // Update proxy configuration display
        if (isRunning) {
            val ports = proxyService?.getProxyPorts()
            if (ports != null) {
                val config = """
                    📡 Local Proxy Configuration
                    HTTP Proxy: 127.0.0.1:${ports.first}
                    SOCKS5 Proxy: 127.0.0.1:${ports.second}
                    
                    🔧 Browser Settings:
                    HTTP Proxy: 127.0.0.1:${ports.first}
                    HTTPS Proxy: 127.0.0.1:${ports.first}
                    SOCKS Host: 127.0.0.1:${ports.second}
                """.trimIndent()
                
                proxyConfigTextView.text = config
            }
        } else {
            proxyConfigTextView.text = "Proxy server not running"
        }
    }
    
    private fun updateProxyStatusUI() {
        val isRunning = proxyService?.isProxyRunning() ?: false
        val connectionsCount = proxyService?.getActiveConnectionsCount() ?: 0
        
        updateProxyStatus(isRunning, if (isRunning) "Active connections: $connectionsCount" else "Stopped")
        
        activeConnectionsTextView.text = "Active Connections: $connectionsCount"
    }
    
    private fun updateProxyConfigDisplay(config: ProxyConfig) {
        val ports = proxyService?.getProxyPorts()
        if (ports != null) {
            val configText = """
                📡 Local Proxy Configuration
                HTTP Proxy: 127.0.0.1:${ports.first}
                SOCKS5 Proxy: 127.0.0.1:${ports.second}
                
                🌐 Remote Server: ${config.host}
                Remote HTTP: ${config.httpPort}
                Remote SOCKS: ${config.socksPort}
                
                📱 Carrier IP: ${config.carrierIP}
            """.trimIndent()
            
            proxyConfigTextView.text = configText
        }
    }
    
    private fun refreshConnectionStatus() {
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val result = myProxyAPI.getConnectionStatus()
                
                when (result) {
                    is ApiResponse.Success -> {
                        val response = result.data
                        if (response.success && response.connectionStatus != null) {
                            val status = response.connectionStatus
                            val statusText = """
                                🔗 Connection Status
                                Connected: ${if (status.isConnected) "✅ YES" else "❌ NO"}
                                Type: ${status.connectionType}
                                Server: ${status.serverLocation}
                                Tunnel IP: ${status.tunnelIP}
                                Carrier IP: ${status.carrierIP}
                                Latency: ${status.latency}
                                
                                📊 Bandwidth
                                Download: ${status.bandwidth.downloadSpeed}
                                Upload: ${status.bandwidth.uploadSpeed}
                                Total Down: ${status.bandwidth.totalDownloaded}
                                Total Up: ${status.bandwidth.totalUploaded}
                            """.trimIndent()
                            
                            connectionStatusTextView.text = statusText
                        } else {
                            connectionStatusTextView.text = "Connection status unavailable"
                        }
                    }
                    is ApiResponse.Error -> {
                        connectionStatusTextView.text = "Error: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                connectionStatusTextView.text = "Network error: ${e.message}"
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun startStatusMonitoring() {
        lifecycleScope.launch {
            while (true) {
                delay(5000) // Update every 5 seconds
                
                if (isBound) {
                    updateProxyStatusUI()
                }
                
                // Refresh connection status every 30 seconds
                if (System.currentTimeMillis() % 30000 < 5000) {
                    refreshConnectionStatus()
                }
            }
        }
    }
    
    private fun performLogout() {
        // Stop proxy server
        proxyService?.stopProxyServer()
        
        // Clear authentication
        myProxyAPI.logout()
        
        // Navigate to login
        startLogin()
    }
    
    private fun startLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
