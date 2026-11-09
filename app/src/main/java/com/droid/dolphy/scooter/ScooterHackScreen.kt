package com.droid.dolphy.scooter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.droid.dolphy.BlurHeaderBox
import com.droid.dolphy.M3SegmentedListItemContainer
import com.droid.dolphy.M3SegmentedListItemSpacing
import com.droid.dolphy.R
import com.droid.dolphy.WavyCircularProgressIndicator

@Composable
fun ScooterHackScreen(
    navController: NavController,
    viewModel: ScooterHackViewModel = viewModel(),
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val showAllDevices by viewModel.showAllDevices.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val scanAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted[Manifest.permission.BLUETOOTH_SCAN] == true
        } else {
            granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }

        if (scanAllowed || granted.values.all { it }) {
            viewModel.startScan(context)
        } else {
            Toast.makeText(context, context.getString(R.string.scooter_perm_denied), Toast.LENGTH_SHORT).show()
        }
    }

    fun ensureScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.startScan(context)
        } else {
            permissionLauncher.launch(perms)
        }
    }

    LaunchedEffect(Unit) {
        ensureScan()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScan()
        }
    }

    BlurHeaderBox(
        title = stringResource(R.string.scooter_hack_title),
        onBack = { navController.popBackStack() },
        accentColor = accent,
        actions = {
            IconButton(onClick = { viewModel.clearDevices() }) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Effacer",
                    tint = colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    if (isScanning) {
                        viewModel.stopScan()
                    } else {
                        ensureScan()
                    }
                },
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.FlashOff else Icons.Default.Refresh,
                    contentDescription = if (isScanning) "Arrêter" else "Scanner",
                    tint = if (isScanning) Color(0xFFFF5252) else accent,
                )
            }
        },
    ) { headerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = headerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // Barre de filtres et d'état du scanner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilterChip(
                    selected = !showAllDevices,
                    onClick = { viewModel.toggleFilterMode() },
                    label = {
                        Text(
                            text = if (!showAllDevices) "🛴 Trottinettes & Flottes" else "📡 Tous les périphériques BLE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.18f),
                        selectedLabelColor = accent,
                        selectedLeadingIconColor = accent,
                    ),
                )

                Text(
                    text = "${devices.size} détecté${if (devices.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(4.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        if (isScanning) {
                            WavyCircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                color = accent,
                                strokeWidth = 4.dp,
                            )
                            Text(
                                text = "Recherche de trottinettes à proximité…",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = "Détection Xiaomi (M365/Pro), Ninebot/Segway, Okai et balises libre-service (Lime, Tier, Bolt, Dott, Bird)…",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ElectricScooter,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp),
                            )
                            Text(
                                text = stringResource(R.string.scooter_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            OutlinedButton(
                                onClick = { ensureScan() },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Lancer l'analyse BLE")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    itemsIndexed(devices, key = { _, d -> d.address }) { index, scooter ->
                        ScooterDeviceCard(
                            scooter = scooter,
                            index = index,
                            count = devices.size,
                            accent = accent,
                            onToggleConnect = { viewModel.toggleConnection(context, scooter) },
                            onSendPayload = { p, l -> viewModel.sendTuningPayload(p, l) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScooterDeviceCard(
    scooter: ScooterDevice,
    index: Int,
    count: Int,
    accent: Color,
    onToggleConnect: () -> Unit,
    onSendPayload: (ByteArray, String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val connected = scooter.isConnected
    val isFleet = scooter.isRentalFleet

    M3SegmentedListItemContainer(
        index = index,
        count = count,
        selected = connected,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFleet) Color(0xFFFF9100).copy(alpha = 0.16f)
                            else accent.copy(alpha = 0.14f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ElectricScooter,
                        contentDescription = null,
                        tint = if (isFleet) Color(0xFFFF9100) else accent,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = scooter.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isFleet) {
                            Spacer(Modifier.width(6.dp))
                            FleetBadge()
                        }
                    }

                    Text(
                        text = "${scooter.brandOrCategory} · ${scooter.address} · ${scooter.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (!scooter.statusMessage.isNullOrBlank()) {
                        Text(
                            text = scooter.statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connected) accent else colorScheme.onSurfaceVariant,
                        )
                    }
                }

                BatteryBadge(percent = scooter.batteryPercent, accent = accent)

                Spacer(Modifier.width(10.dp))

                FilledIconButton(
                    onClick = onToggleConnect,
                    enabled = !scooter.isConnecting,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (connected) Color(0xFFFF5252).copy(alpha = 0.18f) else accent,
                        contentColor = if (connected) Color(0xFFFF5252) else colorScheme.onPrimary,
                        disabledContainerColor = accent.copy(alpha = 0.45f),
                    ),
                ) {
                    Icon(
                        imageVector = if (connected) Icons.Filled.FlashOff else Icons.Filled.Bolt,
                        contentDescription = if (connected) {
                            stringResource(R.string.scooter_disconnect)
                        } else {
                            stringResource(R.string.scooter_connect)
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (connected) {
                Spacer(Modifier.height(12.dp))

                if (isFleet) {
                    // Carte explicative de sécurité pour les trottinettes de flotte commerciale (Lime, Tier, Bolt, Dott)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF9100).copy(alpha = 0.10f),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9100),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Trottinette en libre-service sécurisée (IoT Cloud)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFFF9100),
                                )
                            }
                            Text(
                                text = "Les trottinettes de flottes commerciales (Lime, Tier, Bolt, Dott, Bird, Okai) intègrent une passerelle IoT cellulaire 4G/LTE protégée par chiffrement cryptographique asymétrique. Les commandes UART locales directes sont isolées par le boîtier télématique pour empêcher tout déverrouillage non autorisé sans session serveur opérateur.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Balise BLE : ${scooter.address}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Signal : ${scooter.rssi} dBm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                } else {
                    // Contrôles complets pour trottinettes personnelles (Xiaomi / Ninebot / etc.)
                    Text(
                        text = "Contrôles du véhicule :",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionChip(icon = Icons.Filled.Lock, label = "Verrou", accent = accent) {
                            onSendPayload(ScooterProtocol.COMMAND_LOCK, "Verrouillage")
                        }
                        ActionChip(icon = Icons.Filled.LockOpen, label = "Déverrou", accent = accent) {
                            onSendPayload(ScooterProtocol.COMMAND_UNLOCK, "Déverrouillage")
                        }
                        ActionChip(icon = Icons.Filled.VolumeUp, label = "Klaxon", accent = accent) {
                            onSendPayload(ScooterProtocol.COMMAND_BEEP, "Klaxon/Bip")
                        }
                        ActionChip(icon = Icons.Filled.Bolt, label = "Phares", accent = accent) {
                            onSendPayload(ScooterProtocol.COMMAND_LIGHTS_TOGGLE, "Phares LED")
                        }
                        ActionChip(icon = Icons.Filled.ElectricScooter, label = "Sport 30k", accent = accent) {
                            onSendPayload(ScooterProtocol.COMMAND_SPORT_SPEED_30, "Sport 30km/h")
                        }
                        ActionChip(icon = Icons.Filled.BatteryStd, label = "Éco 15k", accent = accent) {
                            onSendPayload(ScooterProtocol.COMMAND_ECO_SPEED_15, "Mode Éco 15km/h")
                        }
                        ActionChip(icon = Icons.Filled.Bolt, label = "Boost 45k", accent = accent) {
                            onSendPayload(ScooterProtocol.buildSpeedCommand(45), "Boost 45km/h")
                        }
                        ActionChip(icon = Icons.Filled.ElectricScooter, label = "Max 100k", accent = accent) {
                            onSendPayload(ScooterProtocol.buildSpeedCommand(100), "Débridage 100km/h")
                        }
                    }

                    var customSpeed by remember { mutableFloatStateOf(35f) }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Vitesse cible : ${customSpeed.toInt()} km/h (jusqu'à 100)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                        TextButton(
                            onClick = {
                                val sp = customSpeed.toInt()
                                onSendPayload(ScooterProtocol.buildSpeedCommand(sp), "Vitesse $sp km/h")
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("Appliquer (${customSpeed.toInt()}k)", fontWeight = FontWeight.Bold)
                        }
                    }
                    Slider(
                        value = customSpeed,
                        onValueChange = { customSpeed = it },
                        valueRange = 15f..100f,
                        steps = 16,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Firmware Tuning & Payloads :",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = Icons.Filled.Build
                        ActionChip(icon = icon, label = "Max 2G", accent = accent) {
                            onSendPayload(ScooterProtocol.PAYLOAD_MAX_2G, "Max 2G")
                        }
                        ActionChip(icon = icon, label = "F2", accent = accent) {
                            onSendPayload(ScooterProtocol.PAYLOAD_F2, "F2")
                        }
                        ActionChip(icon = icon, label = "Gen 1", accent = accent) {
                            onSendPayload(ScooterProtocol.PAYLOAD_GEN_1, "Gen 1")
                        }
                        ActionChip(icon = icon, label = "Gen 2", accent = accent) {
                            onSendPayload(ScooterProtocol.PAYLOAD_GEN_2, "Gen 2")
                        }
                        ActionChip(icon = icon, label = "Gen 3", accent = accent) {
                            onSendPayload(ScooterProtocol.PAYLOAD_GEN_3, "Gen 3")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FleetBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFFF9100).copy(alpha = 0.20f),
    ) {
        Text(
            text = "FLOTTE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9100),
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun BatteryBadge(percent: Int?, accent: Color) {
    val colorScheme = MaterialTheme.colorScheme
    val icon = if ((percent ?: 0) > 20) Icons.Filled.BatteryFull else Icons.Filled.BatteryStd
    val tint = when {
        percent == null -> colorScheme.onSurfaceVariant
        percent <= 15 -> Color(0xFFFF5252)
        percent <= 35 -> Color(0xFFFFB300)
        else -> Color(0xFF69F0AE)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = if (percent != null) "$percent%" else "—",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
