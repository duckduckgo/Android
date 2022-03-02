/*
 * Copyright (c) 2020 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dummy.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.extensions.historicalExitReasonsByProcessName
import com.duckduckgo.app.global.formatters.time.model.TimePassed
import com.duckduckgo.mobile.android.ui.view.rightDrawable
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ActivityVpnDiagnosticsBinding
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.Companion.SLIDING_WINDOW_DURATION_MS
import com.duckduckgo.mobile.android.vpn.health.CurrentMemorySnapshot
import com.duckduckgo.mobile.android.vpn.health.HealthCheckSubmission
import com.duckduckgo.mobile.android.vpn.health.HealthMetricCounter
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.ADD_TO_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.ADD_TO_TCP_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.ADD_TO_UDP_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.REMOVE_FROM_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.REMOVE_FROM_TCP_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.REMOVE_FROM_UDP_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.SOCKET_CHANNEL_CONNECT_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.SOCKET_CHANNEL_READ_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.SOCKET_CHANNEL_WRITE_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.TUN_READ
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.TUN_READ_IPV4_PACKET
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.TUN_READ_IPV6_PACKET
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.TUN_READ_UNKNOWN_PACKET
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.TUN_WRITE_IO_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.TUN_WRITE_IO_MEMORY_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.UserHealthSubmission
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.squareup.moshi.Moshi
import dagger.android.AndroidInjection
import dummy.ui.VpnDiagnosticsGetUserHealthReportActivity.Companion.RESULT_DATA_KEY_NOTES
import dummy.ui.VpnDiagnosticsGetUserHealthReportActivity.Companion.RESULT_DATA_KEY_STATUS
import dummy.ui.VpnDiagnosticsGetUserHealthReportActivity.Companion.UNDETERMINED_STATUS
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.NumberFormat
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool

class VpnDiagnosticsActivity : DuckDuckGoActivity(), CoroutineScope by MainScope() {

    private lateinit var connectivityManager: ConnectivityManager

    private lateinit var binding: ActivityVpnDiagnosticsBinding

    @Inject lateinit var repository: AppTrackerBlockingStatsRepository

    @Inject lateinit var healthMetricCounter: HealthMetricCounter

    @Inject lateinit var vpnQueues: VpnQueues

    @Inject lateinit var appTPHealthMonitor: AppTPHealthMonitor

    @Inject lateinit var deviceShieldPixels: DeviceShieldPixels

    private val moshi = Moshi.Builder().build()

    private var timerUpdateJob: Job? = null

    private val numberFormatter =
        NumberFormat.getNumberInstance().also { it.maximumFractionDigits = 2 }

    private val jsonAdapter = moshi.adapter(HealthCheckSubmission::class.java).indent("  ")

    private val userHealthReportActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            val userHealthState = data.getStringExtra(RESULT_DATA_KEY_STATUS) ?: UNDETERMINED_STATUS
            val userNotes = data.getStringExtra(RESULT_DATA_KEY_NOTES)
            submitHealthReport(UserHealthSubmission(userHealthState, userNotes))
        }

    private fun submitHealthReport(userReport: UserHealthSubmission) {
        launch(Dispatchers.IO) {
            val currentSystemHealthReport = appTPHealthMonitor.healthState.value
            val topLevelSubmission = HealthCheckSubmission(userReport, currentSystemHealthReport)

            val json = jsonAdapter.toJson(topLevelSubmission)
            Timber.w("Sending health report\n%s", json)

            val encodedData =
                Base64.encodeToString(
                    json.toByteArray(),
                    Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
                )
            deviceShieldPixels.sendHealthMonitorReport(mapOf("data" to encodedData))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVpnDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.trackersToolbar)

        AndroidInjection.inject(this)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // stopTracing()
        configureEventHandlers()
        updateStatus()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appTPHealthMonitor.healthState.collect { healthState ->
                    Timber.i("Health is %s", healthState::class.java.simpleName)

                    if (healthState.isBadHealth) {
                        updateHealthIndicator(R.drawable.ic_baseline_mood_bad_24)
                    } else {
                        updateHealthIndicator(R.drawable.ic_baseline_mood_happy_24)
                    }
                }
            }
        }
    }

    private suspend fun updateHealthIndicator(healthIndicatorIcon: Int) {
        withContext(Dispatchers.Main) {
            binding.healthMetricsLabel.rightDrawable(healthIndicatorIcon)
        }
    }

    private fun configureEventHandlers() {
        binding.clearHealthMetricsButton.setOnClickListener {
            healthMetricCounter.clearAllMetrics()
            updateStatus()
        }

        binding.startVpnButton.setOnClickListener { TrackerBlockingVpnService.startService(this) }

        binding.stopVpnButton.setOnClickListener { TrackerBlockingVpnService.stopService(this) }

        binding.simulateGoodHealth.setOnClickListener {
            appTPHealthMonitor.simulateGoodHealthState()
        }

        binding.simulateBadHealth.setOnClickListener {
            appTPHealthMonitor.simulateBadHealthState()
        }

        binding.simulateCriticalBadHealth.setOnClickListener {
            appTPHealthMonitor.simulateCriticalHealthState()
        }

        binding.noSimulation.setOnClickListener { appTPHealthMonitor.stopHealthSimulation() }

        binding.sendBadHealthReportButton.setOnClickListener {
            userHealthReportActivityResult.launch(
                VpnDiagnosticsGetUserHealthReportActivity.intent(this),
            )
        }
    }

    private fun updateStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val networkInfo = retrieveNetworkStatusInfo()
            val dnsInfo = retrieveDnsInfo()
            val addresses = retrieveIpAddressesInfo(networkInfo)
            val totalAppTrackers = retrieveAppTrackersBlockedInfo()
            val runningTimeFormatted = retrieveRunningTimeInfo()
            val appTrackersBlockedFormatted = generateTrackersBlocked(totalAppTrackers)
            val healthMetricsInfo = retrieveHealthMetricsInfo()
            val memoryInfo = retrieveMemoryMetrics()
            val healthMetricsFormatted = generateHealthMetricsStrings(healthMetricsInfo)

            withContext(Dispatchers.Main) {
                binding.networkAddresses.text = addresses
                binding.meteredConnectionStatus.text =
                    getString(R.string.atp_MeteredConnection, networkInfo.metered.toString())
                binding.vpnStatus.text =
                    getString(R.string.atp_ConnectionStatus, networkInfo.vpn.toString())
                binding.networkAvailable.text =
                    getString(
                        R.string.atp_NetworkAvailable, networkInfo.connectedToInternet.toString()
                    )
                binding.runningTime.text = runningTimeFormatted
                binding.appTrackersBlockedText.text =
                    String.format("App %s", appTrackersBlockedFormatted)
                binding.dnsServersText.text = getString(R.string.atp_DnsServers, dnsInfo)
                binding.healthMetrics.text = healthMetricsFormatted
                binding.memoryMetrics.text = memoryInfo.toString()
            }
        }
    }

    private fun retrieveMemoryMetrics() = CurrentMemorySnapshot(applicationContext)

    private fun generateHealthMetricsStrings(healthMetricsInfo: HealthMetricsInfo): String {
        val healthMetricsStrings = mutableListOf<String>()

        healthMetricsStrings.add(
            String.format(
                """
                    device-to-network queue writes: %d
                      tun reads: %d (rate %s)
                        IPv4 packets: %d (rate %s)
                            unknown packets: %d (rate %s)
                        IPv6 packets: %d (rate %s)
                      queue reads: %s (rate %s)
                        queue TCP reads: %s (rate %s)
                        queue UDP reads: %s (rate %s)
                """.trimIndent(),
                healthMetricsInfo.writtenToDeviceToNetworkQueue,
                healthMetricsInfo.tunPacketReceived,
                calculatePercentage(
                    healthMetricsInfo.writtenToDeviceToNetworkQueue,
                    healthMetricsInfo.tunPacketReceived,
                ),
                healthMetricsInfo.tunIpv4PacketReceived,
                calculatePercentage(
                    healthMetricsInfo.tunIpv4PacketReceived,
                    healthMetricsInfo.tunPacketReceived,
                ),
                healthMetricsInfo.tunUnknownPacketReceived,
                calculatePercentage(
                    healthMetricsInfo.tunUnknownPacketReceived,
                    healthMetricsInfo.tunPacketReceived,
                ),
                healthMetricsInfo.tunIpv6PacketReceived,
                calculatePercentage(
                    healthMetricsInfo.tunIpv6PacketReceived,
                    healthMetricsInfo.tunPacketReceived,
                ),
                healthMetricsInfo.removeFromDeviceToNetworkQueue,
                calculatePercentage(
                    healthMetricsInfo.writtenToDeviceToNetworkQueue,
                    healthMetricsInfo.removeFromDeviceToNetworkQueue,
                ),
                healthMetricsInfo.removeFromTCPDeviceToNetworkQueue,
                calculatePercentage(
                    healthMetricsInfo.writtenToTCPDeviceToNetworkQueue,
                    healthMetricsInfo.removeFromTCPDeviceToNetworkQueue,
                ),
                healthMetricsInfo.removeFromUDPDeviceToNetworkQueue,
                calculatePercentage(
                    healthMetricsInfo.writtenToUDPDeviceToNetworkQueue,
                    healthMetricsInfo.removeFromUDPDeviceToNetworkQueue,
                ),
            ),
        )

        healthMetricsStrings.add(
            String.format(
                "\n\nSocket exceptions:\nRead: %d, Write: %d, Connect: %d",
                healthMetricsInfo.socketReadExceptions,
                healthMetricsInfo.socketWriteExceptions,
                healthMetricsInfo.socketConnectException,
            ),
        )

        healthMetricsStrings.add(
            String.format(
                "\n\nTun write exceptions: %d",
                healthMetricsInfo.tunWriteIOExceptions,
            ),
        )

        healthMetricsStrings.add(
            String.format(
                "\n\nTun write memory exceptions: %d",
                healthMetricsInfo.tunWriteIOMemoryExceptions,
            ),
        )

        healthMetricsStrings.add(
            String.format(
                "\n\nBuffer allocations: %d",
                healthMetricsInfo.bufferAllocations,
            ),
        )

        val sb = StringBuilder()
        healthMetricsStrings.forEach { sb.append(it) }

        return sb.toString()
    }

    private fun calculatePercentage(
        numerator: Long,
        denominator: Long
    ): String {
        if (denominator == 0L) return "0%"
        return String.format(
            "%s%%",
            numberFormatter.format(numerator.toDouble() / denominator * 100),
        )
    }

    private fun retrieveHealthMetricsInfo(): HealthMetricsInfo {
        val timeWindow = System.currentTimeMillis() - SLIDING_WINDOW_DURATION_MS

        val tunPacketReceived = healthMetricCounter.getStat(TUN_READ(), timeWindow)
        val tunIpv4PacketReceived = healthMetricCounter.getStat(TUN_READ_IPV4_PACKET(), timeWindow)
        val tunIpv6PacketReceived = healthMetricCounter.getStat(TUN_READ_IPV6_PACKET(), timeWindow)
        val tunUnknownPacketReceived = healthMetricCounter.getStat(TUN_READ_UNKNOWN_PACKET(), timeWindow)
        val removeFromDeviceToNetworkQueue =
            healthMetricCounter.getStat(REMOVE_FROM_DEVICE_TO_NETWORK_QUEUE(), timeWindow)
        val removeFromTCPDeviceToNetworkQueue =
            healthMetricCounter.getStat(REMOVE_FROM_TCP_DEVICE_TO_NETWORK_QUEUE(), timeWindow)
        val removeFromUDPDeviceToNetworkQueue =
            healthMetricCounter.getStat(REMOVE_FROM_UDP_DEVICE_TO_NETWORK_QUEUE(), timeWindow)
        val writtenToDeviceToNetworkQueue =
            healthMetricCounter.getStat(ADD_TO_DEVICE_TO_NETWORK_QUEUE(), timeWindow)
        val writtenToTCPDeviceToNetworkQueue =
            healthMetricCounter.getStat(ADD_TO_TCP_DEVICE_TO_NETWORK_QUEUE(), timeWindow)
        val writtenToUDPDeviceToNetworkQueue =
            healthMetricCounter.getStat(ADD_TO_UDP_DEVICE_TO_NETWORK_QUEUE(), timeWindow)
        val socketReadExceptions =
            healthMetricCounter.getStat(SOCKET_CHANNEL_READ_EXCEPTION(), timeWindow)
        val socketWriteExceptions =
            healthMetricCounter.getStat(SOCKET_CHANNEL_WRITE_EXCEPTION(), timeWindow)
        val socketConnectExceptions =
            healthMetricCounter.getStat(SOCKET_CHANNEL_CONNECT_EXCEPTION(), timeWindow)
        val tunWriteIOExceptions = healthMetricCounter.getStat(TUN_WRITE_IO_EXCEPTION(), timeWindow)
        val tunWriteIOMemoryExceptions = healthMetricCounter.getStat(TUN_WRITE_IO_MEMORY_EXCEPTION(), timeWindow)
        val bufferAllocations = ByteBufferPool.allocations.get()

        return HealthMetricsInfo(
            tunPacketReceived = tunPacketReceived,
            tunIpv4PacketReceived = tunIpv4PacketReceived,
            tunIpv6PacketReceived = tunIpv6PacketReceived,
            tunUnknownPacketReceived = tunUnknownPacketReceived,
            writtenToDeviceToNetworkQueue = writtenToDeviceToNetworkQueue,
            writtenToTCPDeviceToNetworkQueue = writtenToTCPDeviceToNetworkQueue,
            writtenToUDPDeviceToNetworkQueue = writtenToUDPDeviceToNetworkQueue,
            removeFromDeviceToNetworkQueue = removeFromDeviceToNetworkQueue,
            removeFromTCPDeviceToNetworkQueue = removeFromTCPDeviceToNetworkQueue,
            removeFromUDPDeviceToNetworkQueue = removeFromUDPDeviceToNetworkQueue,
            socketReadExceptions = socketReadExceptions,
            socketWriteExceptions = socketWriteExceptions,
            socketConnectException = socketConnectExceptions,
            tunWriteIOExceptions = tunWriteIOExceptions,
            tunWriteIOMemoryExceptions = tunWriteIOMemoryExceptions,
            bufferAllocations = bufferAllocations
        )
    }

    private fun retrieveHistoricalCrashInfo(): AppExitHistory {
        if (Build.VERSION.SDK_INT < 30) {
            return AppExitHistory()
        }

        val exitReasons =
            applicationContext.historicalExitReasonsByProcessName(
                "com.duckduckgo.mobile.android.vpn:vpn",
                10,
            )
        return AppExitHistory(exitReasons)
    }

    private fun retrieveRestartsHistoryInfo(): AppExitHistory {
        return runBlocking {
            val restarts =
                withContext(Dispatchers.IO) {
                    repository.getVpnRestartHistory().sortedByDescending { it.timestamp }.map {
                        """
                        Restarted on ${it.formattedTimestamp}
                        App exit reason - ${it.reason}
                        """.trimIndent()
                    }
                }

            AppExitHistory(restarts)
        }
    }

    private suspend fun retrieveRunningTimeInfo() =
        generateTimeRunningMessage(
            repository.getRunningTimeMillis({ repository.noStartDate() }).firstOrNull() ?: 0L,
        )

    private suspend fun retrieveAppTrackersBlockedInfo() =
        (repository.getVpnTrackers({ repository.noStartDate() }).firstOrNull() ?: emptyList()).size

    private fun retrieveIpAddressesInfo(networkInfo: NetworkInfo): String {
        return if (networkInfo.networks.isEmpty()) {
            "no addresses"
        } else {
            networkInfo.networks.joinToString(
                "\n\n",
                transform = { "${it.type.type}:\n${it.address}" },
            )
        }
    }

    private fun generateTimeRunningMessage(timeRunningMillis: Long): String {
        return if (timeRunningMillis == 0L) {
            getString(R.string.vpnNotRunYet)
        } else {
            return getString(
                R.string.vpnTimeRunning,
                TimePassed.fromMilliseconds(timeRunningMillis).format(),
            )
        }
    }

    private fun generateTrackersBlocked(totalTrackers: Int): String {
        return if (totalTrackers == 0) {
            applicationContext.getString(R.string.vpnTrackersNone)
        } else {
            return applicationContext.getString(R.string.vpnTrackersBlockedToday, totalTrackers)
        }
    }

    private fun retrieveNetworkStatusInfo(): NetworkInfo {
        val networks = getCurrentNetworkAddresses()
        val metered = connectivityManager.isActiveNetworkMetered
        val vpn = isVpnEnabled()
        val connectedToInternet = isConnectedToInternet()

        return NetworkInfo(
            networks,
            metered = metered,
            vpn = vpn,
            connectedToInternet = connectedToInternet,
        )
    }

    private fun retrieveDnsInfo(): String {
        val dnsServerAddresses = mutableListOf<String>()

        runCatching {
            connectivityManager
                .allNetworks
                .filter { it.isConnected() }
                .mapNotNull { connectivityManager.getLinkProperties(it) }
                .map { it.dnsServers }
                .flatten()
                .forEach { dnsServerAddresses.add(it.hostAddress) }
        }

        return if (dnsServerAddresses.isEmpty()) return "none"
        else
            dnsServerAddresses.joinToString(
                ", ",
            ) { it }
    }

    private fun android.net.Network.isConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager
                .getNetworkCapabilities(this)
                ?.hasCapability(NET_CAPABILITY_INTERNET) == true &&
                connectivityManager
                .getNetworkCapabilities(this)
                ?.hasCapability(NET_CAPABILITY_VALIDATED) == true
        } else {
            isConnectedLegacy(this)
        }
    }

    @Suppress("DEPRECATION")
    private fun isConnectedLegacy(network: android.net.Network): Boolean {
        return connectivityManager.getNetworkInfo(network)?.isConnectedOrConnecting == true
    }

    private fun isConnectedToInternet(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isConnectedToInternetMarshmallowAndNewer()
        } else {
            isConnectedToInternetLegacy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isConnectedToInternetMarshmallowAndNewer(): Boolean {

        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                ?: return false

        return capabilities.hasCapability(NET_CAPABILITY_VALIDATED)
    }

    @Suppress("DEPRECATION")
    private fun isConnectedToInternetLegacy(): Boolean {
        return connectivityManager.activeNetworkInfo?.isConnectedOrConnecting ?: false
    }

    private fun getCurrentNetworkAddresses(): List<Network> {
        val networks = mutableListOf<Network>()

        for (networkInterface in NetworkInterface.getNetworkInterfaces()) {
            for (networkAddress in networkInterface.inetAddresses) {
                if (!networkAddress.isLoopbackAddress) {
                    networks.add(
                        Network(
                            address = networkAddress.hostAddress,
                            type = addressType(address = networkAddress),
                        ),
                    )
                }
            }
        }

        return networks
    }

    private fun isVpnEnabled(): Boolean {
        return connectivityManager.allNetworks
            .mapNotNull { connectivityManager.getNetworkCapabilities(it) }
            .any { it.hasTransport(TRANSPORT_VPN) }
    }

    private fun addressType(address: InetAddress?): NetworkType {
        if (address is Inet6Address) return networkTypeV6
        if (address is Inet4Address) return networkTypeV4
        return networkTypeUnknown
    }

    override fun onStart() {
        super.onStart()

        timerUpdateJob?.cancel()
        timerUpdateJob =
            lifecycleScope.launch {
                while (isActive) {
                    updateStatus()
                    delay(1_000)
                }
            }
    }

    override fun onStop() {
        super.onStop()
        timerUpdateJob?.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.vpn_network_info_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh -> {
                updateStatus()
                true
            }
            R.id.appExitHistory -> {
                val history = retrieveHistoricalCrashInfo()

                AlertDialog.Builder(this)
                    .setTitle(R.string.atp_AppExitsReasonsTitle)
                    .setMessage(history.toString())
                    .setPositiveButton("OK") { _, _ -> }
                    .setNeutralButton("Share") { _, _ ->
                        val intent =
                            Intent(Intent.ACTION_SEND).also {
                                it.type = "text/plain"
                                it.putExtra(Intent.EXTRA_TEXT, history.toString())
                                it.putExtra(Intent.EXTRA_SUBJECT, "Share VPN exit reasons")
                            }
                        startActivity(Intent.createChooser(intent, "Share"))
                    }
                    .show()
                true
            }
            R.id.vpnRestarts -> {
                val restarts = retrieveRestartsHistoryInfo()

                AlertDialog.Builder(this)
                    .setTitle(R.string.atp_AppRestartsTitle)
                    .setMessage(restarts.toString())
                    .setPositiveButton("OK") { _, _ -> }
                    .setNegativeButton("Clean") { _, _ ->
                        runBlocking(Dispatchers.IO) { repository.deleteVpnRestartHistory() }
                    }
                    .setNeutralButton("Share") { _, _ ->
                        val intent =
                            Intent(Intent.ACTION_SEND).also {
                                it.type = "text/plain"
                                it.putExtra(Intent.EXTRA_TEXT, restarts.toString())
                                it.putExtra(Intent.EXTRA_SUBJECT, "Share VPN exit reasons")
                            }
                        startActivity(Intent.createChooser(intent, "Share"))
                    }
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {

        private const val KB_IN_MB = 1024

        fun intent(context: Context): Intent {
            return Intent(context, VpnDiagnosticsActivity::class.java)
        }
    }
}

data class AppExitHistory(val history: List<String> = emptyList()) {
    override fun toString(): String {
        return if (history.isEmpty()) {
            "No exit history available"
        } else {
            history.joinToString(separator = "\n\n") { it }
        }
    }
}

data class HealthMetricsInfo(
    val tunPacketReceived: Long,
    val tunIpv4PacketReceived: Long,
    val tunIpv6PacketReceived: Long,
    val tunUnknownPacketReceived: Long,
    val writtenToDeviceToNetworkQueue: Long,
    val writtenToTCPDeviceToNetworkQueue: Long,
    val writtenToUDPDeviceToNetworkQueue: Long,
    val removeFromDeviceToNetworkQueue: Long,
    val removeFromTCPDeviceToNetworkQueue: Long,
    val removeFromUDPDeviceToNetworkQueue: Long,
    val socketReadExceptions: Long,
    val socketWriteExceptions: Long,
    val socketConnectException: Long,
    val tunWriteIOExceptions: Long,
    val tunWriteIOMemoryExceptions: Long,
    val bufferAllocations: Long,
)

data class NetworkInfo(
    val networks: List<Network>,
    val metered: Boolean,
    val vpn: Boolean,
    val connectedToInternet: Boolean
)

data class Network(
    val address: String,
    val type: NetworkType
)

val networkTypeV4 = NetworkType("IPv4")
val networkTypeV6 = NetworkType("IPv6")
val networkTypeUnknown = NetworkType("unknown")

inline class NetworkType(val type: String)
