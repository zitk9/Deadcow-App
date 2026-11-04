package com.droid.dolphy.tvcast

import com.droid.dolphy.DolphyIconButton

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.InetAddress
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CastDevice(
    val name: String,
    val host: String,
    val port: Int,
    val type: CastType,
    val serviceInfo: NsdServiceInfo? = null
)

enum class CastType {
    CHROMECAST,
    GOOGLE_CAST,
    DIAL,
    DLNA,
    UPNP,
    UNKNOWN
}

class VideoHttpServer(
    private val videoFile: File,
    port: Int = 8080,
) : NanoHTTPD(port) {

    private val mime: String = when (videoFile.extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "video/mp4"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        if (uri == "/" || uri == "/index.html" || uri == "/play") {
            val html = """
                <!DOCTYPE html><html><head><meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width,initial-scale=1"/>
                <title>Dolphy Cast</title>
                <style>body{margin:0;background:#000;display:flex;align-items:center;justify-content:center;height:100vh}
                video,img{max-width:100%;max-height:100vh}</style></head><body>
                ${if (mime.startsWith("image/")) "<img src='/video' autoplay/>"
                else "<video src='/video' controls autoplay playsinline></video>"}
                </body></html>
            """.trimIndent()
            return newFixedLengthResponse(Response.Status.OK, "text/html", html).apply {
                addHeader("Access-Control-Allow-Origin", "*")
            }
        }

        val videoLength = videoFile.length()
        val rangeHeader = session.headers["range"] ?: session.headers["Range"]
        if (rangeHeader != null) {
            val rangeValues = rangeHeader.replace("bytes=", "", ignoreCase = true).split("-")
            val rangeStart = rangeValues[0].toLongOrNull() ?: 0L
            val rangeEnd = rangeValues.getOrNull(1)?.toLongOrNull() ?: (videoLength - 1)
            val contentLength = (rangeEnd - rangeStart + 1).coerceAtLeast(0)

            val stream = FileInputStream(videoFile).apply { skip(rangeStart) }
            return newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                mime,
                stream,
                contentLength,
            ).apply {
                addHeader("Content-Range", "bytes $rangeStart-$rangeEnd/$videoLength")
                addHeader("Accept-Ranges", "bytes")
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Content-Type", mime)
            }
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            mime,
            FileInputStream(videoFile),
            videoLength,
        ).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Content-Type", mime)
            addHeader("Content-Length", videoLength.toString())
        }
    }
}

@Composable
fun SmartTvCastScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedVideo by remember { mutableStateOf<File?>(null) }
    var server by remember { mutableStateOf<VideoHttpServer?>(null) }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val devices = remember { mutableStateListOf<CastDevice>() }
    var isScanning by remember { mutableStateOf(false) }

    val nsdManager = remember { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }
    val activeListeners = remember { mutableListOf<NsdManager.DiscoveryListener>() }

    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().asSequence().toList()
            val wlan = interfaces.firstOrNull { it.isUp && !it.isLoopback && (it.name.startsWith("wlan") || it.name.startsWith("eth")) }
            val ordered = listOfNotNull(wlan) + interfaces.filter { it != wlan && it.isUp && !it.isLoopback }
            for (iface in ordered) {
                for (address in iface.inetAddresses) {
                    if (!address.isLoopbackAddress && !address.isLinkLocalAddress && address.hostAddress?.contains(":") != true) {
                        return address.hostAddress ?: "0.0.0.0"
                    }
                }
            }
            "0.0.0.0"
        } catch (e: Exception) {
            "0.0.0.0"
        }
    }

    fun stopServer() {
        server?.stop()
        server = null
        serverUrl = null
        isPlaying = false
    }

    fun startServer(videoFile: File) {
        stopServer()
        val candidatePorts = listOf(8080, 8081, 8088, 8888, 9090)
        var chosenPort = 8080
        var startedServer: VideoHttpServer? = null

        for (port in candidatePorts) {
            try {
                val newServer = VideoHttpServer(videoFile, port)
                newServer.start()
                startedServer = newServer
                chosenPort = port
                break
            } catch (e: Exception) {
                Log.w("SmartTvCast", "Port $port unavailable: ${e.message}")
            }
        }

        if (startedServer != null) {
            server = startedServer
            val ip = getLocalIpAddress()
            serverUrl = "http://$ip:$chosenPort/video"
            isPlaying = true
            Log.i("SmartTvCast", "Server up: $serverUrl  (html http://$ip:$chosenPort/play)")
        } else {
            Log.e("SmartTvCast", "Failed to start HTTP server on candidate ports")
        }
    }

    fun startSsdpDiscovery() {
        scope.launch(Dispatchers.IO) {
            try {
                val group = java.net.InetAddress.getByName("239.255.255.250")
                val port = 1900
                val searchMsg = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: ssdp:all\r\n\r\n"
                val sendData = searchMsg.toByteArray(Charsets.UTF_8)

                java.net.DatagramSocket().use { socket ->
                    socket.soTimeout = 3000
                    val packet = java.net.DatagramPacket(sendData, sendData.size, group, port)
                    socket.send(packet)

                    val buffer = ByteArray(4096)
                    val recvPacket = java.net.DatagramPacket(buffer, buffer.size)
                    val startTime = System.currentTimeMillis()

                    while (System.currentTimeMillis() - startTime < 4000) {
                        try {
                            socket.receive(recvPacket)
                            val resp = String(recvPacket.data, 0, recvPacket.length, Charsets.UTF_8)
                            val hostIp = recvPacket.address.hostAddress ?: continue
                            if (hostIp == getLocalIpAddress()) continue

                            var locationUrl: String? = null
                            var serverHeader: String? = null
                            var stHeader: String? = null
                            for (line in resp.lines()) {
                                val trimmed = line.trim()
                                val lower = trimmed.lowercase()
                                if (lower.startsWith("location:")) {
                                    locationUrl = trimmed.substring(9).trim()
                                } else if (lower.startsWith("server:")) {
                                    serverHeader = trimmed.substring(7).trim()
                                } else if (lower.startsWith("st:")) {
                                    stHeader = trimmed.substring(3).trim()
                                }
                            }

                            val devName = when {
                                !serverHeader.isNullOrBlank() -> serverHeader.substringBefore("/")
                                !stHeader.isNullOrBlank() && stHeader.contains(":") -> stHeader.substringAfterLast(":")
                                else -> "Smart TV ($hostIp)"
                            }

                            val devPort = try {
                                if (locationUrl != null) java.net.URL(locationUrl).port.takeIf { it > 0 } ?: 8080 else 8080
                            } catch (_: Exception) { 8080 }

                            val devType = when {
                                resp.contains("dial", ignoreCase = true) -> CastType.DIAL
                                resp.contains("google", ignoreCase = true) -> CastType.GOOGLE_CAST
                                else -> CastType.DLNA
                            }

                            withContext(Dispatchers.Main) {
                                if (devices.none { it.host == hostIp }) {
                                    devices.add(
                                        CastDevice(
                                            name = devName,
                                            host = hostIp,
                                            port = devPort,
                                            type = devType,
                                            serviceInfo = null
                                        )
                                    )
                                    Log.d("SmartTvCast", "SSDP device found: $devName @ $hostIp:$devPort ($devType)")
                                }
                            }
                        } catch (_: java.net.SocketTimeoutException) {
                            break
                        } catch (e: Exception) {
                            Log.w("SmartTvCast", "SSDP parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SmartTvCast", "SSDP discovery error: ${e.message}")
            }
        }
    }

    fun startDiscovery() {
        if (isScanning) return
        isScanning = true

        val serviceTypes = listOf("_googlecast._tcp", "_dial._tcp")
        val ndm = nsdManager

        if (ndm != null) {
            for (serviceType in serviceTypes) {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(st: String) {
                        Log.d("SmartTvCast", "Discovery started: $st")
                    }

                    override fun onDiscoveryStopped(st: String) {
                        Log.d("SmartTvCast", "Discovery stopped: $st")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        Log.d("SmartTvCast", "Service found: ${serviceInfo.serviceName} (${serviceInfo.serviceType})")

                        val type = when {
                            serviceInfo.serviceType.contains("_googlecast") -> CastType.CHROMECAST
                            serviceInfo.serviceType.contains("_dial") -> CastType.DIAL
                            serviceInfo.serviceType.contains("_dlna") -> CastType.DLNA
                            serviceInfo.serviceType.contains("_upnp") -> CastType.UPNP
                            else -> CastType.UNKNOWN
                        }

                        try {
                            ndm.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(si: NsdServiceInfo?, errorCode: Int) {
                                    Log.w("SmartTvCast", "Resolve failed: ${si?.serviceName} error=$errorCode")
                                }
                                override fun onServiceResolved(si: NsdServiceInfo) {
                                    val host = si.host?.hostAddress ?: return
                                    val device = CastDevice(
                                        name = si.serviceName,
                                        host = host,
                                        port = si.port,
                                        type = type,
                                        serviceInfo = si
                                    )
                                    scope.launch(Dispatchers.Main) {
                                        if (devices.none { it.host == device.host && it.name == device.name }) {
                                            devices.add(device)
                                            Log.d("SmartTvCast", "Resolved device: ${device.name} @ ${device.host}:${device.port}")
                                        }
                                    }
                                }
                            })
                        } catch (e: Exception) {
                            Log.w("SmartTvCast", "Error resolving ${serviceInfo.serviceName}: ${e.message}")
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        Log.d("SmartTvCast", "Service lost: ${serviceInfo.serviceName}")
                        scope.launch(Dispatchers.Main) {
                            devices.removeAll { it.serviceInfo == serviceInfo }
                        }
                    }

                    override fun onStartDiscoveryFailed(st: String, errorCode: Int) {
                        Log.e("SmartTvCast", "Discovery start failed for $st: $errorCode")
                    }

                    override fun onStopDiscoveryFailed(st: String, errorCode: Int) {
                        Log.e("SmartTvCast", "Discovery stop failed for $st: $errorCode")
                    }
                }

                try {
                    ndm.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                    activeListeners.add(listener)
                } catch (e: Exception) {
                    Log.e("SmartTvCast", "Failed to discover $serviceType", e)
                }
            }
        }

        startSsdpDiscovery()
    }

    fun stopDiscovery() {
        val ndm = nsdManager
        if (ndm != null) {
            for (listener in activeListeners) {
                try {
                    ndm.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    Log.w("SmartTvCast", "Failed to stop discovery listener: ${e.message}")
                }
            }
        }
        activeListeners.clear()
        isScanning = false
    }

    fun castToDevice(device: CastDevice) {
        val url = serverUrl
        if (url == null) {
            Log.e("SmartTvCast", "castToDevice: no serverUrl — pick a file first")
            return
        }
        scope.launch(Dispatchers.IO) {
            Log.i("SmartTvCast", "Casting $url → ${device.name} (${device.type}) ${device.host}:${device.port}")
            sendCastRequest(device, url)
        }
    }

    fun copyUriToTempFile(context: Context, uri: android.net.Uri): File? {
        return try {
            val tempFile = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("SmartTvCast", "Error copying file: ${e.message}")
            null
        }
    }


    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            scope.launch {
                val file = copyUriToTempFile(context, it)
                if (file != null) {
                    selectedVideo = file
                    startServer(file)
                } else {
                    Log.e("SmartTvCast", "Failed to copy video file")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        startDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose {
            stopServer()
            stopDiscovery()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val accent = MaterialTheme.colorScheme.primary
        MaterialBackground(accentColor = accent) {
            Column(modifier = Modifier.fillMaxSize()) {
                SectionTopBar(
                    title = stringResource(R.string.smarttv_cast_title),
                    onBack = { navController.popBackStack() },
                    accentColor = accent,
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Text(
                    text = stringResource(R.string.smarttv_cast_pick_video),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )


                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    if (selectedVideo == null) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    videoPickerLauncher.launch("*/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.smarttv_cast_choose_video),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {

                        val videoUri = remember(selectedVideo) { selectedVideo?.let { androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", it) } }
                        val previewPlayer = remember(videoUri) {
                            videoUri?.let { uri ->
                                ExoPlayer.Builder(context).build().apply {
                                    setMediaItem(MediaItem.fromUri(uri))
                                    repeatMode = Player.REPEAT_MODE_ALL
                                    volume = 0f
                                    trackSelectionParameters = trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                                        .build()
                                    playWhenReady = true
                                    prepare()
                                }
                            }
                        }

                        DisposableEffect(previewPlayer) {
                            onDispose {
                                previewPlayer?.release()
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {

                            if (previewPlayer != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        androidx.media3.ui.PlayerView(ctx).apply {
                                            useController = false
                                            keepScreenOn = true
                                            player = previewPlayer
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { playerView ->
                                        playerView.player = previewPlayer
                                        previewPlayer.let { player ->
                                            if (!player.isPlaying) {
                                                player.prepare()
                                                player.play()
                                            }
                                        }
                                    }
                                )
                            }


                            DolphyIconButton(
                                onClick = {
                                    stopServer()
                                    selectedVideo?.delete()
                                    selectedVideo = null
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.6f),
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }


                if (selectedVideo != null && serverUrl != null) {
                    com.droid.dolphy.AccentButton(
                        onClick = {

                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(serverUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Language, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.smarttv_cast_open_local_site))
                    }
                }


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.smarttv_cast_devices, devices.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.host + it.name }) { device ->
                        CastDeviceRow(
                            device = device,
                            onCastClick = { castToDevice(device) },
                            canCast = isPlaying && serverUrl != null
                        )
                    }

                    if (devices.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Cast,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.smarttv_cast_no_devices),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun CastDeviceRow(
    device: CastDevice,
    onCastClick: () -> Unit,
    canCast: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${device.type} • ${device.host}:${device.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DolphyIconButton(
                onClick = onCastClick,
                enabled = canCast
            ) {
                Icon(
                    Icons.Filled.Cast,
                    contentDescription = null,
                    tint = if (canCast) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private fun sendCastRequest(device: CastDevice, videoUrl: String) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        try {
            when (device.type) {
                CastType.DIAL -> sendDialRequest(device, videoUrl)
                CastType.DLNA -> sendDlnaRequest(device, videoUrl)
                CastType.CHROMECAST, CastType.GOOGLE_CAST -> sendGoogleCastRequest(device, videoUrl)
                else -> sendGenericCastRequest(device, videoUrl)
            }
        } catch (e: Exception) {
            Log.e("SmartTvCast", "Failed to send cast request: ${e.message}", e)
        }
    }
}

private fun sendDialRequest(device: CastDevice, videoUrl: String) {
    try {

        val launchUrl = "http://${device.host}:${device.port}/apps/YouTube"
        val connection = java.net.URL(launchUrl).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "text/plain")
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 5000


        connection.outputStream.use { os ->
            os.write(videoUrl.toByteArray())
        }

        val responseCode = connection.responseCode
        Log.d("SmartTvCast", "DIAL response: $responseCode")

        if (responseCode == 201 || responseCode == 200) {

            val instanceUrl = connection.getHeaderField("Location")
            if (instanceUrl != null) {

                val playConnection = java.net.URL(instanceUrl).openConnection() as java.net.HttpURLConnection
                playConnection.requestMethod = "POST"
                playConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                playConnection.doOutput = true
                playConnection.connectTimeout = 5000
                playConnection.readTimeout = 5000

                val playData = "v=$videoUrl"
                playConnection.outputStream.use { os ->
                    os.write(playData.toByteArray())
                }

                val playResponse = playConnection.responseCode
                Log.d("SmartTvCast", "DIAL play response: $playResponse")
                playConnection.disconnect()
            }
        }
        connection.disconnect()
    } catch (e: Exception) {
        Log.e("SmartTvCast", "DIAL request failed: ${e.message}", e)
    }
}

private fun sendDlnaRequest(device: CastDevice, videoUrl: String) {
    val controlPaths = listOf(
        "/upnp/control/AVTransport1",
        "/AVTransport/control",
        "/MediaRenderer/AVTransport/Control",
        "/_urn:schemas-upnp-org:service:AVTransport_control",
        "/dmr/AVTransport/control",
        "/control/AVTransport1",
        "/AVTransport1/ctrl",
    )
    val meta = buildDlnaMetaData(videoUrl)
    val escapedUri = videoUrl
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    for (path in controlPaths) {
        try {
            val soapUrl = "http://${device.host}:${device.port}$path"
            val setOk = dlnaSoap(
                soapUrl,
                "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI",
                """
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>$escapedUri</CurrentURI>
                  <CurrentURIMetaData>$meta</CurrentURIMetaData>
                </u:SetAVTransportURI>
                """.trimIndent(),
            )
            Log.d("SmartTvCast", "DLNA SetURI $path → $setOk")
            if (setOk !in 200..299 && setOk != 0) continue

            val playOk = dlnaSoap(
                soapUrl,
                "urn:schemas-upnp-org:service:AVTransport:1#Play",
                """
                <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <Speed>1</Speed>
                </u:Play>
                """.trimIndent(),
            )
            Log.d("SmartTvCast", "DLNA Play $path → $playOk")
            if (playOk in 200..299) return
        } catch (e: Exception) {
            Log.w("SmartTvCast", "DLNA path $path failed: ${e.message}")
        }
    }
    sendGenericCastRequest(device, videoUrl.replace("/video", "/play"))
}

private fun buildDlnaMetaData(videoUrl: String): String {
    val title = "Deadcow Cast"
    val didl = """
        <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
         xmlns:dc="http://purl.org/dc/elements/1.1/"
         xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
          <item id="1" parentID="0" restricted="1">
            <dc:title>$title</dc:title>
            <upnp:class>object.item.videoItem</upnp:class>
            <res protocolInfo="http-get:*:video/mp4:*">$videoUrl</res>
          </item>
        </DIDL-Lite>
    """.trimIndent()
    return didl
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

private fun dlnaSoap(url: String, soapAction: String, bodyInner: String): Int {
    return try {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPACTION", "\"$soapAction\"")
        connection.doOutput = true
        connection.connectTimeout = 4000
        connection.readTimeout = 6000
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
 xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
  <s:Body>$bodyInner</s:Body>
</s:Envelope>"""
        connection.outputStream.use { it.write(soapBody.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        connection.disconnect()
        code
    } catch (e: Exception) {
        Log.w("SmartTvCast", "dlnaSoap $url: ${e.message}")
        -1
    }
}

private fun sendGoogleCastRequest(device: CastDevice, videoUrl: String) {
    try {
        val playPage = videoUrl.replace("/video", "/play")
        for (app in listOf("YouTube", "ChromeCast", "DefaultMediaReceiver")) {
            try {
                val castUrl = "http://${device.host}:8008/apps/$app"
                val connection = java.net.URL(castUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "text/plain")
                connection.doOutput = true
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.outputStream.use { it.write(playPage.toByteArray()) }
                val code = connection.responseCode
                Log.d("SmartTvCast", "Cast $app → $code")
                connection.disconnect()
                if (code in 200..299) return
            } catch (e: Exception) {
                Log.w("SmartTvCast", "Cast app $app: ${e.message}")
            }
        }
        sendDialRequest(device, playPage)
        sendGenericCastRequest(device, playPage)
    } catch (e: Exception) {
        Log.e("SmartTvCast", "Google Cast request failed: ${e.message}", e)
    }
}

private fun sendGenericCastRequest(device: CastDevice, videoUrl: String) {
    try {

        val endpoints = listOf(
            "/apps/YouTube",
            "/dial/apps/YouTube",
            "/upnp/control/AVTransport1"
        )

        for (endpoint in endpoints) {
            try {
                val url = "http://${device.host}:${device.port}$endpoint"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                val json = """{"url":"$videoUrl"}"""
                connection.outputStream.use { os ->
                    os.write(json.toByteArray())
                }

                val responseCode = connection.responseCode
                Log.d("SmartTvCast", "Generic cast to $endpoint response: $responseCode")
                connection.disconnect()

                if (responseCode in 200..299) {
                    break
                }
            } catch (e: Exception) {
                Log.d("SmartTvCast", "Endpoint $endpoint failed: ${e.message}")
            }
        }
    } catch (e: Exception) {
        Log.e("SmartTvCast", "Generic cast request failed: ${e.message}", e)
    }
}

