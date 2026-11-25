package com.example.data.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.domain.model.DeviceType
import com.example.domain.model.HotspotState
import com.example.domain.model.NearbyDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class HotspotAndDiscoveryManager(private val context: Context) {

    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _hotspotState = MutableStateFlow(
        HotspotState(
            supports5Ghz = detect5GhzSupport(),
            localIp = resolveLocalIpv4Address(),
            bandLabel = if (detect5GhzSupport()) "5 GHz" else "2.4 GHz",
        ),
    )
    val hotspotState: StateFlow<HotspotState> = _hotspotState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()

    private var localOnlyHotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var localDeviceId: String = ""

    companion object {
        private const val SERVICE_TYPE = "_wifishare._tcp."
    }

    fun hasHotspotPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun updateServerPort(port: Int) {
        _hotspotState.update {
            it.copy(
                serverPort = port,
                localIp = resolveLocalIpv4Address(),
            )
        }
    }

    fun updatePreferredBand(prefer5Ghz: Boolean) {
        val hw5G = detect5GhzSupport()
        val effective5G = prefer5Ghz && hw5G
        _hotspotState.update {
            it.copy(
                supports5Ghz = effective5G,
                bandLabel = if (effective5G) "5 GHz" else "2.4 GHz",
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocalHotspot() {
        val ip = resolveLocalIpv4Address()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || wifiManager == null) {
            _hotspotState.update {
                it.copy(
                    isActive = true,
                    localIp = ip,
                    statusText = "شبكة Wi-Fi المحلية نشطة",
                    errorMessage = null,
                )
            }
            return
        }

        if (!hasHotspotPermission()) {
            _hotspotState.update {
                it.copy(
                    localIp = ip,
                    statusText = "جاهز عبر الشبكة المحلية",
                    errorMessage = null,
                )
            }
            return
        }

        if (localOnlyHotspotReservation != null) {
            _hotspotState.update {
                it.copy(
                    isActive = true,
                    isLocalHotspotOwned = true,
                    localIp = resolveLocalIpv4Address(),
                    statusText = "نقطة الاتصال نشطة",
                    errorMessage = null,
                )
            }
            return
        }

        runCatching {
            wifiManager?.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        localOnlyHotspotReservation = reservation
                        val config = reservation?.softApConfiguration
                        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            config?.ssid ?: "WiFiShare_5G"
                        } else {
                            @Suppress("DEPRECATION")
                            reservation?.wifiConfiguration?.SSID ?: "WiFiShare_5G"
                        }
                        val pass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            config?.passphrase.orEmpty()
                        } else {
                            @Suppress("DEPRECATION")
                            reservation?.wifiConfiguration?.preSharedKey.orEmpty()
                        }
                        _hotspotState.update {
                            it.copy(
                                isActive = true,
                                isLocalHotspotOwned = true,
                                ssid = ssid,
                                passphrase = pass,
                                localIp = resolveLocalIpv4Address(),
                                statusText = "نقطة الاتصال نشطة",
                                errorMessage = null,
                            )
                        }
                    }

                    override fun onStopped() {
                        localOnlyHotspotReservation = null
                        _hotspotState.update {
                            it.copy(
                                isLocalHotspotOwned = false,
                                statusText = "جاهز عبر الشبكة المحلية",
                            )
                        }
                    }

                    override fun onFailed(reason: Int) {
                        localOnlyHotspotReservation = null
                        _hotspotState.update {
                            it.copy(
                                isActive = true,
                                isLocalHotspotOwned = false,
                                localIp = resolveLocalIpv4Address(),
                                statusText = "متاح عبر Wi-Fi المباشر",
                                errorMessage = null,
                            )
                        }
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }.onFailure {
            _hotspotState.update {
                it.copy(
                    isActive = true,
                    localIp = resolveLocalIpv4Address(),
                    statusText = "متاح عبر الشبكة المحلية",
                )
            }
        }
    }

    fun stopLocalHotspot() {
        runCatching { localOnlyHotspotReservation?.close() }
        localOnlyHotspotReservation = null
        _hotspotState.update {
            it.copy(
                isLocalHotspotOwned = false,
                statusText = "متاح عبر الشبكة المحلية",
            )
        }
    }

    fun startNsdAdvertisingAndDiscovery(
        deviceId: String,
        deviceName: String,
        port: Int,
        supports5Ghz: Boolean,
    ) {
        localDeviceId = deviceId
        val manager = nsdManager ?: return
        stopNsd()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "WiFiShare_${deviceId.take(6)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("deviceId", deviceId)
            setAttribute("deviceName", deviceName.take(48))
            setAttribute("deviceType", DeviceType.PHONE.name)
            setAttribute("band5g", supports5Ghz.toString())
        }

        val regListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = regListener
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, regListener)
        }

        val discListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("wifishare", ignoreCase = true)) {
                    resolveNsdService(manager, service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val lostName = service.serviceName
                _discoveredDevices.update { list ->
                    list.filterNot { lostName.contains(it.deviceId.take(6)) }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = discListener
        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNsdService(manager: NsdManager, service: NsdServiceInfo) {
        runCatching {
            manager.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val attrs = resolved.attributes
                        val id = attrs["deviceId"]?.decodeToString() ?: resolved.serviceName
                        if (id == localDeviceId) return
                        val name = attrs["deviceName"]?.decodeToString() ?: resolved.serviceName
                        val typeStr = attrs["deviceType"]?.decodeToString() ?: DeviceType.PHONE.name
                        val is5G = attrs["band5g"]?.decodeToString()?.toBooleanStrictOrNull() ?: true
                        val host = resolved.host?.hostAddress ?: return
                        val port = resolved.port
                        val type = runCatching { DeviceType.valueOf(typeStr) }.getOrDefault(DeviceType.PHONE)

                        val item = NearbyDevice(
                            deviceId = id,
                            deviceName = name,
                            deviceType = type,
                            host = host,
                            port = port,
                            supports5Ghz = is5G,
                            signalHint = if (is5G) "Wi-Fi 5 GHz • مكتشف تلقائياً" else "مكتشف تلقائياً • قريب جداً",
                            isPaired = false,
                            isConnected = false,
                        )
                        _discoveredDevices.update { current ->
                            (current.filterNot { it.deviceId == id } + item)
                        }
                    }
                },
            )
        }
    }

    fun stopNsd() {
        val manager = nsdManager ?: return
        registrationListener?.let { listener ->
            runCatching { manager.unregisterService(listener) }
        }
        discoveryListener?.let { listener ->
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        registrationListener = null
        discoveryListener = null
    }

    private fun detect5GhzSupport(): Boolean {
        return runCatching {
            wifiManager?.is5GHzBandSupported == true
        }.getOrDefault(true)
    }

    fun resolveLocalIpv4Address(): String {
        return runCatching {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val prioritized = interfaces.sortedByDescending { iface ->
                when {
                    iface.name.startsWith("ap") || iface.name.startsWith("swlan") -> 4
                    iface.name.startsWith("wlan") -> 3
                    iface.name.startsWith("p2p") -> 2
                    else -> 1
                }
            }
            for (iface in prioritized) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in Collections.list(iface.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return@runCatching addr.hostAddress ?: "192.168.43.1"
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }
}
