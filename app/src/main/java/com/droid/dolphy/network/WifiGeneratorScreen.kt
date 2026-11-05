package com.droid.dolphy.network

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.qr.QrGenerator
import kotlin.random.Random

/**
 * Gestionnaire du Point d'accès local Android (LocalOnlyHotspot)
 */
object WifiHotspotManager {
    var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    var activeSsid by mutableStateOf<String?>(null)
    var activePassword by mutableStateOf<String?>(null)
    var isStarting by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var qrBitmap by mutableStateOf<Bitmap?>(null)

    val isActive: Boolean
        get() = reservation != null || activeSsid != null

    fun start(
        context: Context,
        onStarted: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val err = "Android 8.0 (API 26) minimum requis pour le Point d'accès local"
            errorMessage = err
            onError?.invoke(err)
            return
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            val err = "Gestionnaire Wi-Fi Android indisponible sur cet appareil"
            errorMessage = err
            onError?.invoke(err)
            return
        }

        isStarting = true
        errorMessage = null

        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation?) {
                    super.onStarted(res)
                    reservation = res
                    isStarting = false

                    var ssid = "Deadcow_Local_AP"
                    var pass = ""

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        res?.softApConfiguration?.let { softAp ->
                            softAp.ssid?.let { ssid = it }
                            softAp.passphrase?.let { pass = it }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        res?.wifiConfiguration?.let { config ->
                            config.SSID?.let { ssid = it.trim('"') }
                            config.preSharedKey?.let { pass = it.trim('"') }
                        }
                    }

                    activeSsid = ssid
                    activePassword = pass

                    val qrPayload = if (pass.isNotBlank()) {
                        "WIFI:T:WPA;S:$ssid;P:$pass;;"
                    } else {
                        "WIFI:T:nopass;S:$ssid;;"
                    }
                    qrBitmap = QrGenerator.generateQrCode(qrPayload, 512)
                    onStarted?.invoke()
                }

                override fun onStopped() {
                    super.onStopped()
                    stop()
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    isStarting = false
                    val msg = when (reason) {
                        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "Erreur : Aucun canal Wi-Fi libre disponible"
                        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC -> "Erreur générale d'initialisation du point d'accès"
                        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Mode incompatible : le partage de connexion ou le Wi-Fi est déjà occupé"
                        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "Partage de connexion désactivé par l'administrateur système"
                        else -> "Échec du démarrage du point d'accès (code d'erreur $reason)"
                    }
                    errorMessage = msg
                    onError?.invoke(msg)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            isStarting = false
            val msg = "Autorisation Wi-Fi refusée par Android : ${e.localizedMessage}"
            errorMessage = msg
            onError?.invoke(msg)
        } catch (e: Exception) {
            isStarting = false
            val msg = "Erreur inattendue : ${e.localizedMessage}"
            errorMessage = msg
            onError?.invoke(msg)
        }
    }

    fun stop() {
        try {
            reservation?.close()
        } catch (_: Exception) {}
        reservation = null
        activeSsid = null
        activePassword = null
        qrBitmap = null
        isStarting = false
    }
}

enum class WifiPresetCategory(val label: String) {
    RICKROLL("🎵 Rickroll"),
    TROLLS("🤪 Phrases & Trolls"),
    RANDOM("🎲 Aléatoire"),
    CUSTOM("✍️ Personnalisé")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiGeneratorScreen(navController: NavController) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary

    var selectedCategory by remember { mutableStateOf(WifiPresetCategory.RICKROLL) }
    var customSsidInput by remember { mutableStateOf("Deadcow_Network_AP") }
    var multilineInput by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Listes de préréglages
    val rickrollSsids = remember {
        listOf(
            "01_Never_Gonna_Give_You_Up",
            "02_Never_Gonna_Let_You_Down",
            "03_Never_Gonna_Run_Around",
            "04_And_Desert_You",
            "05_Never_Gonna_Make_You_Cry",
            "06_Never_Gonna_Say_Goodbye",
            "07_Never_Gonna_Tell_A_Lie",
            "08_And_Hurt_You",
            "09_Weve_Known_Each_Other",
            "10_For_So_Long",
            "11_Your_Hearts_Been_Aching",
            "12_Too_Shy_To_Say_It",
            "13_Inside_We_Both_Know",
            "14_Whats_Been_Going_On",
            "15_We_Know_The_Game",
            "16_And_Were_Gonna_Play_It",
            "17_Dont_Tell_Me_Youre_Too_Blind",
            "18_Never_Gonna_Give_Never_Gonna_Give"
        )
    }

    val trollSsids = remember {
        listOf(
            "FBI Surveillance Van #42",
            "Searching...",
            "Loading...",
            "404 Network Unavailable",
            "Martin Router King",
            "Winternet is Coming",
            "Silence of the LANs",
            "Crier POUET pour le mot de passe",
            "Accès WiFi Payant 5€/min",
            "It Hurts When IP",
            "Drop It Like It's Hotspot",
            "No Free WiFi For You",
            "Pretty Fly for a Wi-Fi",
            "Skynet Global Defense Network",
            "NSA Listening Post #8B",
            "Abraham Linksys",
            "Connectez-vous pour voir des pubs",
            "Virus_Infected_Area_51",
            "NCSIC_Interceptor_Unit",
            "Free_Public_Wifi_No_Password",
            "TellMyWiFiLoveHer",
            "Bill Wi the Science Fi",
            "Panic At The Cisco"
        )
    }

    fun generateRandomSsids(): List<String> {
        val prefixes = listOf("Livebox", "Freebox", "SFR", "Bbox", "TP-Link", "NETGEAR", "Starbucks_Guest", "Airport_VIP_5G", "Free_Wifi_Secure", "AndroidAP", "Tesla_Guest_AP", "Skynet_Node")
        return (1..16).map {
            val prefix = prefixes.random()
            val hex = (1..4).map { "0123456789ABCDEF".random() }.joinToString("")
            "${prefix}_$hex"
        }
    }

    var randomSsids by remember { mutableStateOf(generateRandomSsids()) }

    val currentList = when (selectedCategory) {
        WifiPresetCategory.RICKROLL -> rickrollSsids
        WifiPresetCategory.TROLLS -> trollSsids
        WifiPresetCategory.RANDOM -> randomSsids
        WifiPresetCategory.CUSTOM -> {
            val lines = multilineInput.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isNotEmpty()) lines else listOf(customSsidInput.ifBlank { "Deadcow_Custom_AP" })
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            WifiHotspotManager.start(context)
        } else {
            Toast.makeText(context, "Permissions Wi-Fi requises pour le hotspot", Toast.LENGTH_SHORT).show()
        }
    }

    fun startHotspotWithChecks() {
        val requiredPerms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                requiredPerms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requiredPerms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (requiredPerms.isNotEmpty()) {
            permissionLauncher.launch(requiredPerms.toTypedArray())
        } else {
            WifiHotspotManager.start(context)
        }
    }

    fun copyToClipboard(text: String, label: String = "Wi-Fi SSID") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copié : $text", Toast.LENGTH_SHORT).show()
    }

    fun openSystemTethering(ssidToCopy: String? = null) {
        if (!ssidToCopy.isNullOrBlank()) {
            copyToClipboard(ssidToCopy, "SSID Hotspot")
        }
        val intents = listOf(
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
            Intent("android.settings.WIFI_AP_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        var launched = false
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {}
        }
        if (launched) {
            Toast.makeText(context, "Paramètres ouverts. Collez le nom copié dans votre point d'accès !", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Impossible d'ouvrir directement les paramètres de point d'accès", Toast.LENGTH_SHORT).show()
        }
    }

    MaterialBackground(accentColor = accent) {
        Column(modifier = Modifier.fillMaxSize()) {
            SectionTopBar(
                title = "Générateur Wi-Fi & Hotspot",
                onBack = { navController.popBackStack() },
                accentColor = accent,
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Infos & Guide", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Exporter la liste", tint = accent)
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bannière État du Point d'accès
                item {
                    HotspotStatusCard(
                        accent = accent,
                        onStartHotspot = { startHotspotWithChecks() },
                        onStopHotspot = { WifiHotspotManager.stop() },
                        onOpenSystemTethering = { openSystemTethering(null) },
                        onCopy = { copyToClipboard(it) }
                    )
                }

                // Sélecteur de Catégorie (Rickroll, Trolls, Aléatoire, Personnalisé)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "MODÈLES DE RÉSEAUX WI-FI",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WifiPresetCategory.values().forEach { cat ->
                                val isSelected = selectedCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    label = {
                                        Text(
                                            text = cat.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.2f),
                                        selectedLabelColor = accent
                                    )
                                )
                            }
                        }
                    }
                }

                // Options spécifiques selon la catégorie
                if (selectedCategory == WifiPresetCategory.RANDOM) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentList.size} réseaux simulés",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = { randomSsids = generateRandomSsids() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Régénérer", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (selectedCategory == WifiPresetCategory.CUSTOM) {
                    item {
                        MaterialCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerType = "highest",
                            contentPadding = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Saisie libre de réseaux Wi-Fi",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = customSsidInput,
                                    onValueChange = { customSsidInput = it },
                                    label = { Text("Nom du réseau unique (SSID)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = multilineInput,
                                    onValueChange = { multilineInput = it },
                                    label = { Text("Ou collez une liste de noms (1 par ligne)") },
                                    placeholder = { Text("Never_Gonna_Give_You_Up\nFBI_Van_42\nFree_Wifi") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 6
                                )
                            }
                        }
                    }
                }

                // Titre de section liste
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RÉSEAUX DISPONIBLES (${currentList.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                val allText = currentList.joinToString("\n")
                                copyToClipboard(allText, "Liste Wi-Fi")
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tout copier", fontSize = 11.sp)
                        }
                    }
                }

                // Liste des éléments SSIDs
                itemsIndexed(currentList) { index, ssid ->
                    SsidCardItem(
                        index = index + 1,
                        ssid = ssid,
                        accent = accent,
                        onCopy = { copyToClipboard(ssid, "SSID Wi-Fi") },
                        onApplyToSystemHotspot = { openSystemTethering(ssid) }
                    )
                }
            }
        }
    }

    // Dialogue d'export
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exporter les réseaux Wi-Fi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Sélectionnez le format d'export pour vos scripts, cartes ESP32, Flipper Zero ou outils d'audit :",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val text = currentList.joinToString("\n")
                            copyToClipboard(text, "Liste brute SSIDs")
                            showExportDialog = false
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Format texte brut (1 par ligne)")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val json = "[\n" + currentList.joinToString(",\n") { "  \"$it\"" } + "\n]"
                            copyToClipboard(json, "JSON SSIDs")
                            showExportDialog = false
                        }
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Format JSON")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val espFormat = "# ESP8266 / ESP32 Beacon List\n" + currentList.mapIndexed { idx, s -> "$idx:$s:wpa2" }.joinToString("\n")
                            copyToClipboard(espFormat, "Format ESP32 / Marauder")
                            showExportDialog = false
                        }
                    ) {
                        Icon(Icons.Default.Router, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Format ESP32 / Flipper Marauder")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Dialogue d'information technique
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Fonctionnement des réseaux Wi-Fi") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "1. Point d'accès Connectable (Hotspot) :",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Vous pouvez lancer un point d'accès réel sur ce smartphone. D'autres appareils (smartphones, PC, tablettes) peuvent s'y connecter immédiatement en scannant le QR code ou en saisissant les identifiants.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "2. Pourquoi pas 50 Wi-Fi émis simultanément ?",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Sur Android standard, le firmware de la puce Wi-Fi ne permet pas d'émettre des trames brutes de balises (raw beacon frames) multiples sans mode moniteur (qui requiert un kernel patché type Nexmon ou une clé USB Wi-Fi externe).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Deadcow vous permet donc de créer un vrai point d'accès fonctionnel, ou d'exporter les listes de balises générées vers des modules spécialisés (ESP8266 / ESP32 Marauder / Flipper Zero).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Compris")
                }
            }
        )
    }
}

@Composable
private fun HotspotStatusCard(
    accent: Color,
    onStartHotspot: () -> Unit,
    onStopHotspot: () -> Unit,
    onOpenSystemTethering: () -> Unit,
    onCopy: (String) -> Unit
) {
    val isActive = WifiHotspotManager.isActive
    val isStarting = WifiHotspotManager.isStarting
    val ssid = WifiHotspotManager.activeSsid
    val password = WifiHotspotManager.activePassword
    val qrBitmap = WifiHotspotManager.qrBitmap
    val errorMessage = WifiHotspotManager.errorMessage

    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        containerType = if (isActive) "primary" else "low",
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isActive) Color(0xFF4CAF50) else Color.Gray)
                    )
                    Column {
                        Text(
                            text = if (isActive) "Point d'accès ACTIF" else "Point d'accès Local",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isActive) "Les appareils peuvent s'y connecter" else "Démarrer un hotspot connectable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isActive) {
                    Button(
                        onClick = onStopHotspot,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Arrêter", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onStartHotspot,
                        enabled = !isStarting,
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        if (isStarting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Démarrage...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Lancer", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Message d'erreur s'il y en a
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Affichage des informations et du QR Code quand actif
            AnimatedVisibility(
                visible = isActive && !ssid.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nom du réseau (SSID) :",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ssid.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            if (!password.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Mot de passe :",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = password,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onCopy(ssid.orEmpty()) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copier SSID", tint = accent)
                            }
                            if (!password.isNullOrBlank()) {
                                IconButton(onClick = { onCopy(password) }) {
                                    Icon(Icons.Default.Key, contentDescription = "Copier MDP", tint = accent)
                                }
                            }
                        }
                    }

                    // QR Code
                    qrBitmap?.let { bmp ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "QR Code de connexion Wi-Fi",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = "📱 Scannez avec n'importe quel smartphone pour vous connecter instantanément",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Bouton Partage Système
            OutlinedButton(
                onClick = onOpenSystemTethering,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ouvrir les paramètres Point d'accès du téléphone", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SsidCardItem(
    index: Int,
    ssid: String,
    accent: Color,
    onCopy: () -> Unit,
    onApplyToSystemHotspot: () -> Unit
) {
    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        containerType = "low",
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = index.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = accent
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ssid,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Préréglage Wi-Fi 2.4/5GHz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copier SSID",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onApplyToSystemHotspot) {
                    Icon(
                        Icons.Default.WifiTethering,
                        contentDescription = "Appliquer au partage système",
                        modifier = Modifier.size(18.dp),
                        tint = accent
                    )
                }
            }
        }
    }
}
