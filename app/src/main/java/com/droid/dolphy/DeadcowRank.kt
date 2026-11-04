package com.droid.dolphy

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject

enum class DeadcowRankTier(
    val title: String,
    val badge: String,
    val minScore: Long,
    val color: Color,
    val description: String,
) {
    NEOPHYTE("🌱 Veau Débutant", "🌱", 0L, Color(0xFF81C784), "Premiers pas dans l'écosystème cyber Deadcow."),
    SCOUT("⚡ Scout des Fréquences", "⚡", 500L, Color(0xFF64B5F6), "Exploration active des bandes BLE, IR et NFC."),
    SENTINEL("🛡️ Sentinelle Bovine", "🛡️", 1_500L, Color(0xFFFFB74D), "Maîtrise des protocoles radio et émissions."),
    WARRIOR("⚔️ Guerrier Deadcow", "⚔️", 3_500L, Color(0xFFFF8A65), "Combattant chevronné des interférences et paquets."),
    CYBER_BULL("🐂 Cyber Taureau", "🐂", 7_500L, Color(0xFFE57373), "Force brute du spectre électromagnétique."),
    WAVE_LORD("👑 Seigneur des Ondes", "👑", 15_000L, Color(0xFFBA68C8), "Domination avancée des communications sans-fil."),
    APEX_LEGEND("🌌 Légende Apex Deadcow", "🌌", 30_000L, Color(0xFFFFD700), "Le sommet ultime de l'élite Deadcow.")
}

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val level: Int,
    val score: Long,
    val tier: DeadcowRankTier,
    val isCurrentUser: Boolean = false,
    val detail: String = ""
)

object DeadcowRankSystem {

    private const val PREFS_NAME = "deadcow_peers_rank"
    private const val KEY_PEERS = "discovered_peers_json"

    fun calculateScore(state: DolphyState): Long {
        return (state.icounter.toLong() * 10L) +
            (state.blePacketsSent.toLong() / 4L) +
            (state.nfcReadCount.toLong() * 15L) +
            (state.irSendCount.toLong() * 10L) +
            (state.badHidRunCount.toLong() * 25L) +
            (state.nfcEmulateCount.toLong() * 20L) +
            (state.nrfDevicesFound.toLong() * 5L) +
            (state.wifiBruteSuccessCount.toLong() * 50L)
    }

    fun getTier(score: Long): DeadcowRankTier {
        return DeadcowRankTier.values().lastOrNull { score >= it.minScore } ?: DeadcowRankTier.NEOPHYTE
    }

    fun getNextTier(score: Long): DeadcowRankTier? {
        return DeadcowRankTier.values().firstOrNull { score < it.minScore }
    }

    fun getProgressToNextTier(score: Long): Float {
        val current = getTier(score)
        val next = getNextTier(score) ?: return 1.0f
        val range = (next.minScore - current.minScore).toFloat()
        if (range <= 0) return 1.0f
        return ((score - current.minScore).toFloat() / range).coerceIn(0f, 1f)
    }

    fun recordDiscoveredPeer(context: Context, peerName: String, peerScore: Long, peerLevel: Int) {
        if (peerName.isBlank()) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PEERS, "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            var found = false
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("name").equals(peerName, ignoreCase = true)) {
                    obj.put("score", maxOf(obj.getLong("score"), peerScore))
                    obj.put("level", maxOf(obj.getInt("level"), peerLevel))
                    obj.put("timestamp", System.currentTimeMillis())
                    found = true
                    break
                }
            }
            if (!found) {
                val obj = JSONObject().apply {
                    put("name", peerName)
                    put("score", peerScore)
                    put("level", peerLevel)
                    put("timestamp", System.currentTimeMillis())
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_PEERS, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getDiscoveredPeers(context: Context): List<LeaderboardEntry> {
        val result = mutableListOf<LeaderboardEntry>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PEERS, "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val score = obj.optLong("score", 0L)
                val lvl = obj.optInt("level", 1)
                val name = obj.optString("name", "Pair Inconnu")
                result.add(
                    LeaderboardEntry(
                        rank = 0,
                        name = name,
                        level = lvl,
                        score = score,
                        tier = getTier(score),
                        isCurrentUser = false,
                        detail = "Pair découvert"
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    fun getCommunityLeaderboard(state: DolphyState, context: Context? = null): Pair<List<LeaderboardEntry>, Int> {
        val userScore = calculateScore(state)
        val userName = state.dolphinName.ifBlank { "Deadcow-User" }
        val userLevel = state.level
        val userTier = getTier(userScore)

        val userEntry = LeaderboardEntry(
            rank = 1,
            name = "$userName (Vous)",
            level = userLevel,
            score = userScore,
            tier = userTier,
            isCurrentUser = true,
            detail = "Votre compte"
        )

        val allPeers = mutableListOf<LeaderboardEntry>()
        if (context != null) {
            allPeers.addAll(getDiscoveredPeers(context))
        }

        allPeers.add(userEntry)
        allPeers.sortByDescending { it.score }

        var userRank = 1
        val rankedList = allPeers.mapIndexed { index, entry ->
            val rank = index + 1
            if (entry.isCurrentUser) userRank = rank
            entry.copy(rank = rank)
        }

        return rankedList to userRank
    }
}

@Composable
fun DeadcowLeaderboardDialog(
    dolphyState: DolphyState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Stats réelles, 1 = Pairs & Classement, 2 = Paliers
    var refreshTrigger by remember { mutableStateOf(0) }

    val totalScore = remember(dolphyState, refreshTrigger) {
        DeadcowRankSystem.calculateScore(dolphyState)
    }
    val currentTier = remember(totalScore) {
        DeadcowRankSystem.getTier(totalScore)
    }
    val nextTier = remember(totalScore) {
        DeadcowRankSystem.getNextTier(totalScore)
    }
    val progress = remember(totalScore) {
        DeadcowRankSystem.getProgressToNextTier(totalScore)
    }
    val (leaderboard, userRank) = remember(dolphyState, refreshTrigger) {
        DeadcowRankSystem.getCommunityLeaderboard(dolphyState, context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(currentTier.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = currentTier.badge, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Classement Deadcow",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Rang #${userRank} · ${currentTier.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = currentTier.color,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { refreshTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Score card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Score Cyber Réel : $totalScore pts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Niveau ${dolphyState.level}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = currentTier.color,
                            trackColor = currentTier.color.copy(alpha = 0.2f)
                        )

                        Spacer(Modifier.height(4.dp))

                        if (nextTier != null) {
                            val ptsNeeded = nextTier.minScore - totalScore
                            Text(
                                text = "Encore $ptsNeeded pts pour débloquer ${nextTier.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Rang maximal atteint ! Vous êtes une légende.",
                                style = MaterialTheme.typography.labelSmall,
                                color = currentTier.color
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Stats Réelles", style = MaterialTheme.typography.labelMedium) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Pairs (${leaderboard.size})", style = MaterialTheme.typography.labelMedium) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Paliers", style = MaterialTheme.typography.labelMedium) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Tab Content
                when (selectedTab) {
                    0 -> {
                        // Real stats breakdown
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                StatDetailRow("Activité & XP générale", "${dolphyState.icounter} actions", "+${dolphyState.icounter * 10L} pts")
                            }
                            item {
                                StatDetailRow("Paquets BLE émis", "${dolphyState.blePacketsSent} paquets", "+${dolphyState.blePacketsSent / 4L} pts")
                            }
                            item {
                                StatDetailRow("Scripts BadUSB / HID exécutés", "${dolphyState.badHidRunCount} runs", "+${dolphyState.badHidRunCount * 25L} pts")
                            }
                            item {
                                StatDetailRow("Lectures de tags NFC", "${dolphyState.nfcReadCount} lectures", "+${dolphyState.nfcReadCount * 15L} pts")
                            }
                            item {
                                StatDetailRow("Émulations de tags NFC", "${dolphyState.nfcEmulateCount} émulations", "+${dolphyState.nfcEmulateCount * 20L} pts")
                            }
                            item {
                                StatDetailRow("Signaux Infrarouge émis", "${dolphyState.irSendCount} signaux", "+${dolphyState.irSendCount * 10L} pts")
                            }
                            item {
                                StatDetailRow("Appareils radio NRF découverts", "${dolphyState.nrfDevicesFound} appareils", "+${dolphyState.nrfDevicesFound * 5L} pts")
                            }
                            item {
                                StatDetailRow("Audits Wi-Fi validés", "${dolphyState.wifiBruteSuccessCount} succès", "+${dolphyState.wifiBruteSuccessCount * 50L} pts")
                            }
                        }
                    }
                    1 -> {
                        // Discovered peers + user
                        if (leaderboard.size <= 1) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Classement décentralisé 100% réel. Aucun faux bot ! Les autres utilisateurs Deadcow détectés via BLE ou Chat apparaîtront ici.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(leaderboard) { entry ->
                                val isUser = entry.isCurrentUser
                                val bg = if (isUser) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                                val borderMod = if (isUser) {
                                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                } else Modifier

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(borderMod)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = when (entry.rank) {
                                                1 -> "🥇"
                                                2 -> "🥈"
                                                3 -> "🥉"
                                                else -> "#${entry.rank}"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = entry.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isUser) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Niv. ${entry.level} · ${entry.tier.title}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = entry.tier.color
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${entry.score} pts",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // Tiers list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(DeadcowRankTier.values().toList()) { tier ->
                                val unlocked = totalScore >= tier.minScore
                                val bg = if (unlocked) {
                                    tier.color.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = tier.badge,
                                            fontSize = 20.sp,
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = tier.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (unlocked) tier.color else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = tier.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (unlocked) "DÉBLOQUÉ" else "${tier.minScore} pts",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (unlocked) tier.color else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatDetailRow(label: String, countText: String, ptsText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = ptsText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
