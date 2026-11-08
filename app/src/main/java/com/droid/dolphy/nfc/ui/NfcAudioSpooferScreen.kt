package com.droid.dolphy.nfc.ui

import com.droid.dolphy.DolphyIconButton

import android.app.Activity
import android.nfc.NfcAdapter
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.droid.dolphy.R
import com.droid.dolphy.qr.QrAudioSpooferViewModel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcAudioSpooferScreen(
    navController: NavController,
    viewModel: QrAudioSpooferViewModel = viewModel(),
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val logs by viewModel.logs.collectAsState()
    val isStarted by viewModel.isStarted.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val pageType by viewModel.pageType.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val activity = context as? Activity
    val adapter = remember(activity) { activity?.let { NfcAdapter.getDefaultAdapter(it) } }
    val nfcReady = adapter != null && adapter.isEnabled

    val spoofUrl = if (serverIp != null) {
        if (serverIp!!.startsWith("http")) serverIp!! else "http://$serverIp"
<<<<<<< HEAD
    } else "En attente..."
=======
    } else "Waiting..."
>>>>>>> upstream/main

    val infinite = rememberInfiniteTransition(label = "nfc_spoof_animation")
    val scale by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "dolphin_scale",
    )

    var expanded by remember { mutableStateOf(false) }
    val pages = listOf(
<<<<<<< HEAD
        "dolphy" to "Deadcow (Par défaut)",
        "wifi" to "Wi-Fi Gratuit",
        "error" to "Erreur (Style Chrome)",
        "vpn" to "Service VPN"
=======
        "dolphy" to "Dolphy (Default)",
        "wifi" to "Free Wi-Fi",
        "error" to "Error (Chrome style)",
        "vpn" to "VPN Service"
>>>>>>> upstream/main
    )

    val showFirstTimeInfo by viewModel.showFirstTimeInfo.collectAsState()

    if (showFirstTimeInfo) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFirstTimeInfo() },
            title = { Text(stringResource(R.string.audio_spoof_info_title), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(R.string.audio_spoof_info_text), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                com.droid.dolphy.AccentButton(onClick = { viewModel.dismissFirstTimeInfo() }) {
                    Text(stringResource(R.string.got_it), color = accentColor)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("NFC Audio Spoof", color = Color.White) },
                navigationIcon = {
                    DolphyIconButton(onClick = { navController.popBackStack() }) {
<<<<<<< HEAD
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
=======
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
>>>>>>> upstream/main
                    }
                },
                actions = {
                    if (!isStarted) {
                        DolphyIconButton(onClick = { viewModel.startServer() }) {
<<<<<<< HEAD
                            Icon(Icons.Default.PlayArrow, "Démarrer", tint = accentColor)
                        }
                    } else {
                        DolphyIconButton(onClick = { viewModel.stopServer() }) {
                            Icon(Icons.Default.Stop, "Arrêter", tint = Color.Red)
=======
                            Icon(Icons.Default.PlayArrow, "Start", tint = accentColor)
                        }
                    } else {
                        DolphyIconButton(onClick = { viewModel.stopServer() }) {
                            Icon(Icons.Default.Stop, "Stop", tint = Color.Red)
>>>>>>> upstream/main
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isStarted && nfcReady) {
                        Image(
                            painter = painterResource(id = R.drawable.nfc_dolphin_emulation_51x64_transparent),
<<<<<<< HEAD
                            contentDescription = "Émulation NFC Active",
=======
                            contentDescription = "NFC emulation active",
>>>>>>> upstream/main
                            modifier = Modifier
                                .size(width = 153.dp, height = 192.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale),
                            colorFilter = ColorFilter.tint(accentColor),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
<<<<<<< HEAD
                            "ÉMULATION NDEF URL",
=======
                            "NDEF URL EMULATION",
>>>>>>> upstream/main
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    } else {
                        if (!nfcReady) {
                            Icon(
                                imageVector = Icons.Default.Close,
<<<<<<< HEAD
                                contentDescription = "NFC Indisponible",
=======
                                contentDescription = "NFC unavailable",
>>>>>>> upstream/main
                                modifier = Modifier.size(120.dp),
                                tint = accentColor
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.passport_bad2_46x49),
<<<<<<< HEAD
                                contentDescription = "Prêt à démarrer",
=======
                                contentDescription = "Ready to start",
>>>>>>> upstream/main
                                modifier = Modifier
                                    .size(width = 138.dp, height = 147.dp),
                                colorFilter = ColorFilter.tint(Color.Gray),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
<<<<<<< HEAD
                            if (!nfcReady) "NFC DÉSACTIVÉ" else "PRÊT À DÉMARRER",
=======
                            if (!nfcReady) "NFC DISABLED" else "READY TO START",
>>>>>>> upstream/main
                            color = if (!nfcReady) accentColor else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (!nfcReady) {
                            Text(
<<<<<<< HEAD
                                "Veuillez activer le NFC dans les paramètres",
=======
                                "Please enable NFC in settings",
>>>>>>> upstream/main
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(spoofUrl, color = Color.Gray, fontSize = 12.sp)
                if (isStarted && serverIp != null) {
                    val clipboard = LocalClipboardManager.current
                    val context = LocalContext.current
                    DolphyIconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(spoofUrl))
<<<<<<< HEAD
                            Toast.makeText(context, "URL copiée dans le presse-papiers", Toast.LENGTH_SHORT).show()
=======
                            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
>>>>>>> upstream/main
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
<<<<<<< HEAD
                            contentDescription = "Copier l'URL",
=======
                            contentDescription = "Copy URL",
>>>>>>> upstream/main
                            tint = accentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))


<<<<<<< HEAD
            Text("CHOISISSEZ LE TYPE DE PAGE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
=======
            Text("SELECT PAGE TYPE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
>>>>>>> upstream/main
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
<<<<<<< HEAD
                            pages.find { it.first == pageType }?.second ?: "Choisissez une page",
=======
                            pages.find { it.first == pageType }?.second ?: "Select a page",
>>>>>>> upstream/main
                            modifier = Modifier.weight(1f),
                            color = Color.White
                        )
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f).background(Color(0xFF1A1A1A))
                ) {
                    pages.forEach { (type, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = Color.White) },
                            onClick = {
                                viewModel.setPageType(type)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))


            val deviceCount by viewModel.deviceCount.collectAsState()
            val customAudios by viewModel.customAudioList.collectAsState()

            val audioPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let { viewModel.addCustomAudio(it) }
            }

<<<<<<< HEAD
            Text("TABLE D'EFFETS SONORES", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Text("Connecté : $deviceCount appareil(s)", color = accentColor, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
=======
            Text("SOUND PANEL", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Text("Connected: $deviceCount devices", color = accentColor, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
>>>>>>> upstream/main
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundButton("", Icons.Default.Email, accentColor) { viewModel.sendCommand("PLAY:iphone_message.mp3") }
                SoundButton("", Icons.Default.Call, accentColor) { viewModel.sendCommand("PLAY:ios_call.mp3") }
            }

            customAudios.chunked(2).forEach { pair ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundButton("", null, accentColor, "🎵") { viewModel.sendCommand("PLAY:${pair[0]}") }
                    if (pair.size > 1) {
                        SoundButton("", null, accentColor, "🎵") { viewModel.sendCommand("PLAY:${pair[1]}") }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundButton("", null, accentColor, "🐷") { viewModel.sendCommand("PLAY:vizg_svini.mp3") }
                SoundButton("", Icons.Default.Add, Color.Gray) {
                    audioPickerLauncher.launch("audio/*")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))


            var ttsText by remember { mutableStateOf("") }
<<<<<<< HEAD
            Text("SYNTHÈSE VOCALE (TTS)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
=======
            Text("TEXT TO SPEECH", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
>>>>>>> upstream/main
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = ttsText,
                    onValueChange = { ttsText = it },
                    modifier = Modifier.weight(1f),
<<<<<<< HEAD
                    placeholder = { Text("Entrez du texte...", fontSize = 14.sp) },
=======
                    placeholder = { Text("Enter text...", fontSize = 14.sp) },
>>>>>>> upstream/main
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = accentColor,
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                com.droid.dolphy.AccentButton(
                    onClick = {
                        if (ttsText.isNotBlank()) {
                            viewModel.sendCommand("TTS:$ttsText")
                            ttsText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(52.dp)
                ) {
<<<<<<< HEAD
                    Text("Parler", color = Color.Black, fontWeight = FontWeight.Bold)
=======
                    Text("Say", color = Color.Black, fontWeight = FontWeight.Bold)
>>>>>>> upstream/main
                }
            }

            Spacer(modifier = Modifier.height(24.dp))


<<<<<<< HEAD
            Text("JOURNAUX DU TERMINAL", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
=======
            Text("TERMINAL LOGS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
>>>>>>> upstream/main
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(12.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs) { log ->
                        Text(
                            text = "> $log",
                            color = accentColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun RowScope.SoundButton(label: String, icon: ImageVector?, color: Color, emoji: String? = null, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.height(80.dp).weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (emoji != null) {
                Text(emoji, fontSize = 32.sp)
            } else if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(36.dp))
            }
            if (label.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

