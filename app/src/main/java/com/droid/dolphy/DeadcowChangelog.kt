package com.droid.dolphy

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChangelogEntry(
    val version: String,
    val date: String,
    val isCurrent: Boolean = false,
    val items: List<String>
)

object DeadcowChangelog {
    private const val PREFS = "DeadcowChangelogPrefs"
    private const val KEY_LAST_SEEN_CHANGELOG = "last_seen_changelog_version"

    val ENTRIES = listOf(
        ChangelogEntry(
            version = "v1.6",
            date = "Novembre 2026",
            isCurrent = true,
            items = listOf(
                "📡 Bluetooth & BLE : Correction complète de l'émission pour Samsung Watch, Galaxy Buds, Android Fast Pair, Windows Swift Pair et iOS Continuity.",
                "⌚ Samsung Watch : Restauration de l'émission Legacy standard sur canaux 37, 38, 39 pour déclencher les fenêtres d'appairage sur One UI.",
                "🎧 Samsung Galaxy Buds : Réactivation de la réponse de scan (Scan Response 0x0075) requise pour faire apparaître le pop-up.",
                "🛡️ Compatibilité Android 13/14/15 : Correction des erreurs RECEIVER_EXPORTED et ajout de la permission FOREGROUND_SERVICE_CONNECTED_DEVICE (clavier HID & stress test).",
                "⚡ Fiabilité L2CAP & HID : Élimination des plantages sur Android < Q et stabilisation des boucles d'émission matérielles.",
                "📢 Nouveau Journal des modifications : Affichage des nouveautés au démarrage et prévisualisation du changelog lors des notifications de mise à jour."
            )
        ),
        ChangelogEntry(
            version = "v1.5",
            date = "Novembre 2026",
            items = listOf(
                "🔄 Détection dynamique de l'adaptateur Bluetooth (résolution des cas où le Bluetooth est activé après le lancement).",
                "📻 Prise en charge des appareils ne signalant pas le multi-advertising dans leur firmware mais dotés d'un émetteur BLE fonctionnel.",
                "⏱️ Dwell time fiabilisé à 180ms pour éviter la saturation du contrôleur Bluetooth."
            )
        ),
        ChangelogEntry(
            version = "v1.4",
            date = "Novembre 2026",
            items = listOf(
                "🐮 Mascotte Deadcow : Nouvelles animations 128x64 pixels (Level Up, Skull, Matrix) et portraits passeport.",
                "🎨 Personnalisation : Nouvelles palettes de couleurs d'accentuation et thèmes Expressive."
            )
        ),
        ChangelogEntry(
            version = "v1.3",
            date = "Novembre 2026",
            items = listOf(
                "🚀 Mises à jour automatiques OTA : Détection et installation automatique des nouvelles versions depuis GitHub Releases."
            )
        )
    )

    fun hasSeenChangelog(context: Context, version: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_SEEN_CHANGELOG, "") == version
    }

    fun markChangelogSeen(context: Context, version: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_SEEN_CHANGELOG, version).apply()
    }
}

@Composable
fun ChangelogDialog(
    currentVersion: String,
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = primaryColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "JOURNAL DES MODIFICATIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "Nouveautés Deadcow",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(DeadcowChangelog.ENTRIES) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (entry.isCurrent) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        ),
                        border = if (entry.isCurrent) {
                            BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f))
                        } else null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = entry.version,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (entry.isCurrent) primaryColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (entry.isCurrent) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = primaryColor,
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text(
                                                text = "Actuelle",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = entry.date,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            entry.items.forEach { bullet ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor
                                    )
                                    Text(
                                        text = bullet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        runCatching { uriHandler.openUri("https://github.com/zitk9/Deadcow-App/releases") }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Voir toutes les releases GitHub",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AccentButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Compris !",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    )
}
