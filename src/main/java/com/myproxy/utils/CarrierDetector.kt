package com.myproxy.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.telephony.SignalStrength
import android.telephony.PhoneStateListener
import android.os.Build
import java.net.NetworkInterface
import java.util.*

class CarrierDetector(private val context: Context) {
    
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    data class CarrierInfo(
        val carrierName: String,
        val carrierCode: String,
        val networkType: String,
        val signalStrength: Int,
        val isRoaming: Boolean,
        val localIP: String,
        val networkOperator: String
    )
    
    /**
     * Get comprehensive carrier information
     */
    fun getCarrierInfo(): CarrierInfo {
        return CarrierInfo(
            carrierName = getCarrierName(),
            carrierCode = getCarrierCode(),
            networkType = getNetworkType(),
            signalStrength = getSignalStrength(),
            isRoaming = telephonyManager.isNetworkRoaming,
            localIP = getLocalIP(),
            networkOperator = telephonyManager.networkOperator ?: "Unknown"
        )
    }
    
    /**
     * Get carrier name
     */
    private fun getCarrierName(): String {
        return telephonyManager.networkOperatorName ?: "Unknown"
    }
    
    /**
     * Get carrier code (MCC+MNC)
     */
    private fun getCarrierCode(): String {
        return telephonyManager.networkOperator ?: "Unknown"
    }
    
    /**
     * Get detailed network type
     */
    private fun getNetworkType(): String {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                getCellularNetworkType()
            }
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "Unknown"
        }
    }
    
    /**
     * Get cellular network type details
     */
    private fun getCellularNetworkType(): String {
        return when (telephonyManager.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "3G HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "3G HSUPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "3G EVDO Rev 0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "3G EVDO Rev A"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G EVDO Rev B"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "2G 1xRTT"
            TelephonyManager.NETWORK_TYPE_CDMA -> "2G CDMA"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GSM -> "2G GSM"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G iDEN"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "3G eHRPD"
            else -> "Cellular (Unknown)"
        }
    }
    
    /**
     * Get signal strength
     */
    private fun getSignalStrength(): Int {
        // This is a simplified version - in real implementation you'd use PhoneStateListener
        return -1 // Placeholder - requires phone state listener for real values
    }
    
    /**
     * Get local IP address (carrier assigned)
     */
    fun getLocalIP(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        val hostAddress = address.hostAddress
                        
                        // Filter IPv4 addresses
                        if (hostAddress != null && !hostAddress.contains(":")) {
                            // Prefer cellular interfaces
                            if (networkInterface.name.contains("rmnet") || 
                                networkInterface.name.contains("ccmni") ||
                                networkInterface.name.contains("pdp")) {
                                return hostAddress
                            }
                            
                            // Fallback to any valid IP
                            if (!hostAddress.startsWith("192.168.") && 
                                !hostAddress.startsWith("10.") &&
                                !hostAddress.startsWith("172.")) {
                                return hostAddress
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unknown"
    }
    
    /**
     * Get all network interfaces information
     */
    fun getNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val interfaces = mutableListOf<NetworkInterfaceInfo>()
        
        try {
            val networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in networkInterfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                val ipAddresses = addresses.mapNotNull { it.hostAddress }
                
                interfaces.add(
                    NetworkInterfaceInfo(
                        name = networkInterface.name,
                        displayName = networkInterface.displayName ?: networkInterface.name,
                        isUp = networkInterface.isUp,
                        isLoopback = networkInterface.isLoopback,
                        ipAddresses = ipAddresses
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return interfaces
    }
    
    /**
     * Check if device is using mobile data
     */
    fun isUsingMobileData(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }
    
    /**
     * Check if device is using WiFi
     */
    fun isUsingWiFi(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
    
    /**
     * Get detailed carrier information as formatted string
     */
    fun getDetailedCarrierInfo(): String {
        val info = getCarrierInfo()
        val interfaces = getNetworkInterfaces()
        
        return buildString {
            appendLine("📱 Carrier Information")
            appendLine("Carrier: ${info.carrierName}")
            appendLine("Code: ${info.carrierCode}")
            appendLine("Network: ${info.networkType}")
            appendLine("Roaming: ${if (info.isRoaming) "Yes" else "No"}")
            appendLine("Local IP: ${info.localIP}")
            appendLine()
            appendLine("🌐 Network Interfaces")
            interfaces.forEach { iface ->
                if (iface.isUp && iface.ipAddresses.isNotEmpty()) {
                    appendLine("${iface.name}: ${iface.ipAddresses.joinToString(", ")}")
                }
            }
        }
    }
    
    data class NetworkInterfaceInfo(
        val name: String,
        val displayName: String,
        val isUp: Boolean,
        val isLoopback: Boolean,
        val ipAddresses: List<String>
    )
}
