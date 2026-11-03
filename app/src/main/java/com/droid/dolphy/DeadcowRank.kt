package com.droid.dolphy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

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
    val isCurrentUser: Boolean = false
)

object DeadcowRankSystem {

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

    fun getCommunityLeaderboard(state: DolphyState): Pair<List<LeaderboardEntry>, Int> {
        val userScore = calculateScore(state)
        val userName = state.dolphinName.ifBlank { "Deadcow-User" }
        val userLevel = state.level
        val userTier = getTier(userScore)

        val benchmarkPeers = listOf(
            Triple("Apex-Bovine-01", 42, 68_400L),
            Triple("Cyber-Bull-99", 35, 49_200L),
            Triple("Shadow-Heifer", 28, 31_500L),
            Triple("Matrix-Moo", 22, 22_100L),
            Triple("Dead-Vaquero", 18, 14_800L),
            Triple("Stealth-Cow", 14, 9_500L),
            Triple("Frequency-Bull", 9, 5_800L),
            Triple("Radio-Calf", 6, 2_900L),
            Triple("Wave-Scout", 4, 1_200L),
            Triple("Neophyte-Moo", 2, 450L)
        )

        val all = mutableListOf<LeaderboardEntry>()
        val userEntry = LeaderboardEntry(
            rank = 1,
            name = "$userName (Vous)",
            level = userLevel,
            score = userScore,
            tier = userTier,
            isCurrentUser = true
        )

        var userAdded = false
        val sortedList = mutableListOf<LeaderboardEntry>()

        val rawList = benchmarkPeers.map { (name, lvl, pts) ->
            LeaderboardEntry(0, name, lvl, pts, getTier(pts), false)
        }.toMutableList()

        rawList.add(userEntry)
        rawList.sortByDescending { it.score }

        var userRank = 1
        rawList.forEachIndexed { index, item ->
            val rank = index + 1
            if (item.isCurrentUser) userRank = rank
            sortedList.add(item.copy(rank = rank))
        }

        return sortedList.take(10) to userRank
    }
}

@Composable
fun DeadcowLeaderboardDialog(
    dolphyState: DolphyState,
    onDismiss: () -> Unit
) {
    val totalScore = DeadcowRankSystem.calculateScore(dolphyState)
    val currentTier = DeadcowRankSystem.getTier(totalScore)
    val nextTier = DeadcowRankSystem.getNextTier(totalScore)
    val progress = DeadcowRankSystem.getProgressToNextTier(totalScore)
    val (leaderboard, userRank) = DeadcowRankSystem.getCommunityLeaderboard(dolphyState)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
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

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Score card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Points Cyber : $totalScore pts",
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

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "🏆 Top 10 Cyber Bovins :",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                // Leaderboard list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 280.dp),
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

                Spacer(Modifier.height(14.dp))

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
