package com.myproxy.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.myproxy.api.WireGuardConfig
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class MyProxyVpnService : VpnService() {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    
    companion object {
        private const val TAG = "MyProxyVpnService"
        const val ACTION_START_VPN = "com.myproxy.START_VPN"
        const val ACTION_STOP_VPN = "com.myproxy.STOP_VPN"
        const val EXTRA_WIREGUARD_CONFIG = "wireguard_config"
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> {
                val config = intent.getParcelableExtra<WireGuardConfig>(EXTRA_WIREGUARD_CONFIG)
                if (config != null) {
                    startVpn(config)
                }
            }
            ACTION_STOP_VPN -> {
                stopVpn()
            }
        }
        return START_STICKY
    }
    
    private fun startVpn(config: WireGuardConfig) {
        if (isRunning) {
            Log.w(TAG, "VPN is already running")
            return
        }
        
        try {
            // Create VPN interface
            val builder = Builder()
                .setSession("MyProxy VPN")
                .addAddress(config.peerIp.split("/")[0], 32)
                .addRoute("0.0.0.0", 0)
                .setMtu(config.mtu)
            
            // Add DNS servers
            config.dns.forEach { dns ->
                builder.addDnsServer(dns)
            }
            
            // Establish VPN interface
            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                isRunning = true
                Log.i(TAG, "VPN interface established")
                
                // Start packet processing
                scope.launch {
                    processPackets(config)
                }
                
                // Send success broadcast
                sendBroadcast(Intent("com.myproxy.VPN_CONNECTED"))
            } else {
                Log.e(TAG, "Failed to establish VPN interface")
                sendBroadcast(Intent("com.myproxy.VPN_ERROR"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            sendBroadcast(Intent("com.myproxy.VPN_ERROR"))
        }
    }
    
    private fun stopVpn() {
        isRunning = false
        
        try {
            vpnInterface?.close()
            vpnInterface = null
            
            Log.i(TAG, "VPN stopped")
            sendBroadcast(Intent("com.myproxy.VPN_DISCONNECTED"))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
        }
        
        scope.cancel()
        stopSelf()
    }
    
    private suspend fun processPackets(config: WireGuardConfig) = withContext(Dispatchers.IO) {
        val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
        val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
        
        try {
            // Create UDP channel for WireGuard communication
            val channel = DatagramChannel.open()
            channel.connect(InetSocketAddress(config.endpoint.split(":")[0], config.port))
            
            val packet = ByteArray(32767)
            val buffer = ByteBuffer.allocate(32767)
            
            while (isRunning) {
                // Read packet from VPN interface
                val length = vpnInput.read(packet)
                if (length > 0) {
                    // Process packet (simplified - real WireGuard implementation needed)
                    buffer.clear()
                    buffer.put(packet, 0, length)
                    buffer.flip()
                    
                    // Send to WireGuard server
                    channel.write(buffer)
                    
                    // Read response
                    buffer.clear()
                    val responseLength = channel.read(buffer)
                    if (responseLength > 0) {
                        buffer.flip()
                        buffer.get(packet, 0, responseLength)
                        
                        // Write back to VPN interface
                        vpnOutput.write(packet, 0, responseLength)
                    }
                }
            }
            
            channel.close()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing packets", e)
            if (isRunning) {
                stopVpn()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
    
    /**
     * Check if VPN is running
     */
    fun isVpnRunning(): Boolean = isRunning
    
    /**
     * Get VPN statistics (placeholder)
     */
    fun getVpnStats(): VpnStats {
        return VpnStats(
            isConnected = isRunning,
            bytesIn = 0, // Would track actual bytes in real implementation
            bytesOut = 0,
            packetsIn = 0,
            packetsOut = 0,
            connectionTime = if (isRunning) System.currentTimeMillis() else 0
        )
    }
    
    data class VpnStats(
        val isConnected: Boolean,
        val bytesIn: Long,
        val bytesOut: Long,
        val packetsIn: Long,
        val packetsOut: Long,
        val connectionTime: Long
    )
}
