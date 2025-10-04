package com.myproxy.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// API Response wrapper
sealed class ApiResponse<out T> {
    data class Success<out T>(val data: T) : ApiResponse<T>()
    data class Error(val message: String) : ApiResponse<Nothing>()
}

// Request Models
@Parcelize
data class MobileLoginRequest(
    val username: String,
    val password: String,
    val deviceInfo: DeviceInfo
) : Parcelable

@Parcelize
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val osVersion: String,
    val appVersion: String,
    val carrier: String,
    val networkType: String,
    val carrierIP: String = ""
) : Parcelable

@Parcelize
data class TunnelConnectRequest(
    val mobileUserId: String,
    val deviceInfo: TunnelDeviceInfo,
    val tunnelType: String
) : Parcelable

@Parcelize
data class TunnelDeviceInfo(
    val deviceId: String,
    val carrierIP: String,
    val publicIP: String,
    val carrier: String,
    val networkType: String,
    val location: LocationInfo
) : Parcelable

@Parcelize
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val country: String
) : Parcelable

@Parcelize
data class WireGuardGenerateRequest(
    val mobileUserId: String,
    val serverLocation: String,
    val encryptionLevel: String
) : Parcelable

// Response Models
@Parcelize
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val mobileUser: MobileUser? = null,
    val serverConfig: ServerConfig? = null
) : Parcelable

@Parcelize
data class MobileUser(
    val id: String,
    val username: String,
    val mobileName: String,
    val status: String,
    val subscriptionExpiry: String,
    val loginEnabled: Boolean
) : Parcelable

@Parcelize
data class ServerConfig(
    val proxyHost: String,
    val httpPort: Int,
    val socksPort: Int,
    val serverPublicIP: String
) : Parcelable

@Parcelize
data class TunnelResponse(
    val success: Boolean,
    val message: String,
    val tunnelConfig: TunnelConfig? = null,
    val mobileUser: MobileUser? = null,
    val connectionId: String? = null,
    val carrierInfo: CarrierInfo? = null
) : Parcelable

@Parcelize
data class TunnelConfig(
    val serverId: String,
    val serverHost: String,
    val serverPort: Int,
    val proxyPort: Int,
    val mobileUserCredentials: MobileUserCredentials,
    val tunnelSettings: TunnelSettings
) : Parcelable

@Parcelize
data class MobileUserCredentials(
    val username: String,
    val password: String
) : Parcelable

@Parcelize
data class TunnelSettings(
    val method: String,
    val keepAlive: Boolean,
    val timeout: Int,
    val retryAttempts: Int,
    val encryptionKey: String,
    val compressionEnabled: Boolean
) : Parcelable

@Parcelize
data class CarrierInfo(
    val detectedCarrier: String,
    val originalIP: String,
    val tunnelIP: String
) : Parcelable

@Parcelize
data class WireGuardConfigResponse(
    val success: Boolean,
    val message: String,
    val config: WireGuardConfig? = null,
    val qrCode: String? = null
) : Parcelable

@Parcelize
data class WireGuardConfig(
    val id: String,
    val peerIp: String,
    val serverIp: String,
    val port: Int,
    val endpoint: String,
    val publicKey: String,
    val privateKey: String,
    val allowedIps: String,
    val dns: List<String>,
    val persistentKeepalive: Int,
    val mtu: Int
) : Parcelable

@Parcelize
data class ConnectionStatusResponse(
    val success: Boolean,
    val connectionStatus: ConnectionStatus? = null
) : Parcelable

@Parcelize
data class ConnectionStatus(
    val isConnected: Boolean,
    val connectionType: String,
    val connectedSince: String,
    val serverLocation: String,
    val tunnelIP: String,
    val carrierIP: String,
    val bandwidth: BandwidthInfo,
    val latency: String,
    val encryption: String
) : Parcelable

@Parcelize
data class BandwidthInfo(
    val downloadSpeed: String,
    val uploadSpeed: String,
    val totalDownloaded: String,
    val totalUploaded: String
) : Parcelable

// Proxy Configuration
@Parcelize
data class ProxyConfig(
    val host: String,
    val httpPort: Int,
    val socksPort: Int,
    val username: String,
    val password: String,
    val carrierIP: String,
    val isActive: Boolean = false
) : Parcelable
