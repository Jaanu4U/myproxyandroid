package com.myproxy.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.myproxy.R
import com.myproxy.api.ProxyConfig
import com.myproxy.ui.DashboardActivity
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class ProxyServerService : Service() {
    
    private val binder = ProxyServerBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val executor = Executors.newCachedThreadPool()
    
    // Proxy servers
    private var httpProxyServer: ServerSocket? = null
    private var socksProxyServer: ServerSocket? = null
    
    // Active connections
    private val activeConnections = ConcurrentHashMap<String, Socket>()
    
    // Configuration
    private var proxyConfig: ProxyConfig? = null
    private var isRunning = false
    
    // Listeners
    private var statusListener: ((Boolean, String) -> Unit)? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "proxy_service_channel"
        private const val HTTP_PROXY_PORT = 8888
        private const val SOCKS_PROXY_PORT = 1888
    }
    
    inner class ProxyServerBinder : Binder() {
        fun getService(): ProxyServerService = this@ProxyServerService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    /**
     * Start proxy server with configuration
     */
    fun startProxyServer(config: ProxyConfig) {
        if (isRunning) {
            stopProxyServer()
        }
        
        proxyConfig = config
        
        scope.launch {
            try {
                // Start HTTP proxy server
                startHttpProxyServer()
                
                // Start SOCKS5 proxy server
                startSocksProxyServer()
                
                isRunning = true
                
                withContext(Dispatchers.Main) {
                    statusListener?.invoke(true, "Proxy servers started successfully")
                    updateNotification("Proxy Active - HTTP:$HTTP_PROXY_PORT, SOCKS:$SOCKS_PROXY_PORT")
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusListener?.invoke(false, "Failed to start proxy: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Stop proxy server
     */
    fun stopProxyServer() {
        isRunning = false
        
        // Close all active connections
        activeConnections.values.forEach { socket ->
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        activeConnections.clear()
        
        // Close servers
        httpProxyServer?.close()
        socksProxyServer?.close()
        
        httpProxyServer = null
        socksProxyServer = null
        
        statusListener?.invoke(false, "Proxy servers stopped")
        updateNotification("Proxy Inactive")
    }
    
    /**
     * Start HTTP proxy server
     */
    private suspend fun startHttpProxyServer() = withContext(Dispatchers.IO) {
        httpProxyServer = ServerSocket(HTTP_PROXY_PORT)
        
        while (isRunning && httpProxyServer?.isClosed == false) {
            try {
                val clientSocket = httpProxyServer?.accept()
                clientSocket?.let { socket ->
                    executor.submit {
                        handleHttpProxyConnection(socket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    throw e
                }
            }
        }
    }
    
    /**
     * Start SOCKS5 proxy server
     */
    private suspend fun startSocksProxyServer() = withContext(Dispatchers.IO) {
        socksProxyServer = ServerSocket(SOCKS_PROXY_PORT)
        
        while (isRunning && socksProxyServer?.isClosed == false) {
            try {
                val clientSocket = socksProxyServer?.accept()
                clientSocket?.let { socket ->
                    executor.submit {
                        handleSocksProxyConnection(socket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    throw e
                }
            }
        }
    }
    
    /**
     * Handle HTTP proxy connection
     */
    private fun handleHttpProxyConnection(clientSocket: Socket) {
        val connectionId = "${clientSocket.remoteSocketAddress}_${System.currentTimeMillis()}"
        activeConnections[connectionId] = clientSocket
        
        try {
            val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val output = clientSocket.getOutputStream()
            
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            
            if (parts.size >= 3) {
                val method = parts[0]
                val url = parts[1]
                
                when (method.uppercase()) {
                    "CONNECT" -> handleHttpsConnect(clientSocket, url, output)
                    else -> handleHttpRequest(clientSocket, requestLine, input, output)
                }
            }
            
        } catch (e: Exception) {
            // Connection error
        } finally {
            activeConnections.remove(connectionId)
            try {
                clientSocket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Handle HTTPS CONNECT method
     */
    private fun handleHttpsConnect(clientSocket: Socket, url: String, output: OutputStream) {
        try {
            val config = proxyConfig ?: return
            val parts = url.split(":")
            val host = parts[0]
            val port = if (parts.size > 1) parts[1].toInt() else 443
            
            // Connect to remote server through main proxy
            val remoteSocket = Socket()
            val proxyAddress = InetSocketAddress(config.host, config.httpPort)
            remoteSocket.connect(proxyAddress, 10000)
            
            // Send CONNECT request to main proxy with authentication
            val proxyOutput = remoteSocket.getOutputStream()
            val auth = "${config.username}:${config.password}"
            val encodedAuth = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
            
            val connectRequest = "CONNECT $url HTTP/1.1\r\n" +
                    "Host: $url\r\n" +
                    "Proxy-Authorization: Basic $encodedAuth\r\n" +
                    "Connection: Keep-Alive\r\n\r\n"
            
            proxyOutput.write(connectRequest.toByteArray())
            proxyOutput.flush()
            
            // Read response from main proxy
            val proxyInput = BufferedReader(InputStreamReader(remoteSocket.getInputStream()))
            val response = proxyInput.readLine()
            
            if (response?.contains("200") == true) {
                // Send success response to client
                output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                output.flush()
                
                // Start tunneling data
                tunnelData(clientSocket, remoteSocket)
            } else {
                output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
            }
            
        } catch (e: Exception) {
            try {
                output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Handle regular HTTP request
     */
    private fun handleHttpRequest(clientSocket: Socket, requestLine: String, input: BufferedReader, output: OutputStream) {
        try {
            val config = proxyConfig ?: return
            
            // Connect to main proxy server
            val proxySocket = Socket()
            val proxyAddress = InetSocketAddress(config.host, config.httpPort)
            proxySocket.connect(proxyAddress, 10000)
            
            val proxyOutput = proxySocket.getOutputStream()
            val proxyInput = proxySocket.getInputStream()
            
            // Forward request with authentication
            val auth = "${config.username}:${config.password}"
            val encodedAuth = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
            
            proxyOutput.write("$requestLine\r\n".toByteArray())
            proxyOutput.write("Proxy-Authorization: Basic $encodedAuth\r\n".toByteArray())
            
            // Forward headers
            var line: String?
            while (input.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                proxyOutput.write("$line\r\n".toByteArray())
            }
            proxyOutput.write("\r\n".toByteArray())
            proxyOutput.flush()
            
            // Forward response
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (proxyInput.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                output.flush()
            }
            
            proxySocket.close()
            
        } catch (e: Exception) {
            try {
                output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Handle SOCKS5 proxy connection
     */
    private fun handleSocksProxyConnection(clientSocket: Socket) {
        val connectionId = "${clientSocket.remoteSocketAddress}_${System.currentTimeMillis()}"
        activeConnections[connectionId] = clientSocket
        
        try {
            val config = proxyConfig ?: return
            
            // Connect to main SOCKS5 proxy
            val proxySocket = Socket()
            val proxyAddress = InetSocketAddress(config.host, config.socksPort)
            proxySocket.connect(proxyAddress, 10000)
            
            // Authenticate with main proxy
            authenticateWithSocksProxy(proxySocket, config.username, config.password)
            
            // Start tunneling data between client and main proxy
            tunnelData(clientSocket, proxySocket)
            
        } catch (e: Exception) {
            // Connection error
        } finally {
            activeConnections.remove(connectionId)
            try {
                clientSocket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Authenticate with SOCKS5 proxy
     */
    private fun authenticateWithSocksProxy(socket: Socket, username: String, password: String) {
        val output = socket.getOutputStream()
        val input = socket.getInputStream()
        
        // Send authentication methods
        output.write(byteArrayOf(0x05, 0x01, 0x02)) // SOCKS5, 1 method, username/password
        output.flush()
        
        // Read server response
        val response = ByteArray(2)
        input.read(response)
        
        if (response[1] == 0x02.toByte()) {
            // Send username/password
            val authData = ByteArrayOutputStream()
            authData.write(0x01) // Version
            authData.write(username.length)
            authData.write(username.toByteArray())
            authData.write(password.length)
            authData.write(password.toByteArray())
            
            output.write(authData.toByteArray())
            output.flush()
            
            // Read authentication response
            val authResponse = ByteArray(2)
            input.read(authResponse)
            
            if (authResponse[1] != 0x00.toByte()) {
                throw Exception("SOCKS5 authentication failed")
            }
        }
    }
    
    /**
     * Tunnel data between two sockets
     */
    private fun tunnelData(socket1: Socket, socket2: Socket) {
        val job1 = scope.launch {
            try {
                socket1.getInputStream().copyTo(socket2.getOutputStream())
            } catch (e: Exception) {
                // Connection closed
            }
        }
        
        val job2 = scope.launch {
            try {
                socket2.getInputStream().copyTo(socket1.getOutputStream())
            } catch (e: Exception) {
                // Connection closed
            }
        }
        
        runBlocking {
            job1.join()
            job2.join()
        }
        
        try {
            socket1.close()
            socket2.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Set status listener
     */
    fun setStatusListener(listener: (Boolean, String) -> Unit) {
        statusListener = listener
    }
    
    /**
     * Get proxy status
     */
    fun isProxyRunning(): Boolean = isRunning
    
    /**
     * Get active connections count
     */
    fun getActiveConnectionsCount(): Int = activeConnections.size
    
    /**
     * Get proxy ports
     */
    fun getProxyPorts(): Pair<Int, Int> = Pair(HTTP_PROXY_PORT, SOCKS_PROXY_PORT)
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Proxy Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MyProxy local proxy server"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(status: String = "Proxy Inactive"): Notification {
        val intent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyProxy Service")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(status: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopProxyServer()
        scope.cancel()
        executor.shutdown()
    }
}
