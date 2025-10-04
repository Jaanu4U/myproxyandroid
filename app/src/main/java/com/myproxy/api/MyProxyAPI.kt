package com.myproxy.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.telephony.TelephonyManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import java.util.*

class MyProxyAPI(private val context: Context) {
    
    private val baseUrl = "https://myproxy.co.in/api"
    private var authToken: String? = null
    
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
                setLenient()
            }
        }
        install(Logging) {
            level = LogLevel.BODY
        }
    }
    
    // Store authenticated user data
    var currentUser: MobileUser? = null
        private set
    
    var serverConfig: ServerConfig? = null
        private set
    
    /**
     * Login mobile user and retrieve carrier IP
     */
    suspend fun loginMobileUser(username: String, password: String): ApiResponse<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val deviceInfo = collectDeviceInfo()
                val carrierIP = getCarrierIP()
                
                val request = MobileLoginRequest(
                    username = username,
                    password = password,
                    deviceInfo = deviceInfo.copy(carrierIP = carrierIP)
                )
                
                val response: LoginResponse = httpClient.post("$baseUrl/auth/mobile-login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                
                if (response.success) {
                    authToken = response.token
                    currentUser = response.mobileUser
                    serverConfig = response.serverConfig
                }
                
                ApiResponse.Success(response)
                
            } catch (e: Exception) {
                ApiResponse.Error("Login failed: ${e.message}")
            }
        }
    }
    
    /**
     * Establish tunnel connection with carrier IP
     */
    suspend fun establishTunnel(): ApiResponse<TunnelResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val user = currentUser ?: return@withContext ApiResponse.Error("User not logged in")
                val token = authToken ?: return@withContext ApiResponse.Error("No auth token")
                
                val deviceInfo = collectTunnelDeviceInfo()
                
                val request = TunnelConnectRequest(
                    mobileUserId = user.id,
                    deviceInfo = deviceInfo,
                    tunnelType = "android_app"
                )
                
                val response: TunnelResponse = httpClient.post("$baseUrl/mobile-users/tunnel-connect") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $token")
                    setBody(request)
                }
                
                ApiResponse.Success(response)
                
            } catch (e: Exception) {
                ApiResponse.Error("Tunnel connection failed: ${e.message}")
            }
        }
    }
    
    /**
     * Get WireGuard configuration
     */
    suspend fun generateWireGuardConfig(): ApiResponse<WireGuardConfigResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val user = currentUser ?: return@withContext ApiResponse.Error("User not logged in")
                val token = authToken ?: return@withContext ApiResponse.Error("No auth token")
                
                val request = WireGuardGenerateRequest(
                    mobileUserId = user.id,
                    serverLocation = "mumbai",
                    encryptionLevel = "high"
                )
                
                val response: WireGuardConfigResponse = httpClient.post("$baseUrl/mobile-users/wireguard/generate") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $token")
                    setBody(request)
                }
                
                ApiResponse.Success(response)
                
            } catch (e: Exception) {
                ApiResponse.Error("WireGuard config generation failed: ${e.message}")
            }
        }
    }
    
    /**
     * Get connection status
     */
    suspend fun getConnectionStatus(): ApiResponse<ConnectionStatusResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val user = currentUser ?: return@withContext ApiResponse.Error("User not logged in")
                val token = authToken ?: return@withContext ApiResponse.Error("No auth token")
                
                val response: ConnectionStatusResponse = httpClient.get("$baseUrl/mobile-users/connection-status") {
                    parameter("mobileUserId", user.id)
                    header("Authorization", "Bearer $token")
                }
                
                ApiResponse.Success(response)
                
            } catch (e: Exception) {
                ApiResponse.Error("Failed to get connection status: ${e.message}")
            }
        }
    }
    
    /**
     * Collect device information including carrier IP
     */
    private fun collectDeviceInfo(): DeviceInfo {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        return DeviceInfo(
            deviceId = getDeviceId(),
            deviceName = android.os.Build.MODEL,
            osVersion = "Android ${android.os.Build.VERSION.RELEASE}",
            appVersion = getAppVersion(),
            carrier = telephonyManager.networkOperatorName ?: "Unknown",
            networkType = getNetworkType(),
            carrierIP = getCarrierIP()
        )
    }
    
    /**
     * Collect tunnel device information
     */
    private fun collectTunnelDeviceInfo(): TunnelDeviceInfo {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        return TunnelDeviceInfo(
            deviceId = getDeviceId(),
            carrierIP = getCarrierIP(),
            publicIP = getPublicIP(),
            carrier = telephonyManager.networkOperatorName ?: "Unknown",
            networkType = getNetworkType(),
            location = LocationInfo(
                latitude = 0.0, // You can implement location services
                longitude = 0.0,
                city = "Unknown",
                country = "India"
            )
        )
    }
    
    /**
     * Get device ID
     */
    private fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    
    /**
     * Get app version
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    
    /**
     * Get network type
     */
    private fun getNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                when (telephonyManager.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA -> "3G"
                    TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
                    else -> "Cellular"
                }
            }
            else -> "Unknown"
        }
    }
    
    /**
     * Get carrier IP address (local IP)
     */
    private fun getCarrierIP(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
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
        return "192.168.1.100" // Fallback
    }
    
    /**
     * Get public IP (placeholder - would need external service)
     */
    private fun getPublicIP(): String {
        // In real implementation, you'd call an external service
        return "203.0.113.50" // Placeholder
    }
    
    /**
     * Clear authentication data
     */
    fun logout() {
        authToken = null
        currentUser = null
        serverConfig = null
    }
    
    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return authToken != null && currentUser != null
    }
}
