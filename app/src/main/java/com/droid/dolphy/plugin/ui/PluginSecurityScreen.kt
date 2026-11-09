package com.droid.dolphy.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.BlurHeaderBox
import com.droid.dolphy.DolphySwitch
import com.droid.dolphy.MaterialButton
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.plugin.PluginDownloadPolicy
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.store.PluginStoreRepository

@Composable
fun PluginSecurityScreen(navController: NavController) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { PluginStoreRepository.initialize(context) }
    val ask by PluginDownloadPolicy.askBeforeDownload.collectAsState()
    val autoUpdate by PluginStoreRepository.autoUpdate.collectAsState()
    val translate by PluginStoreRepository.translateEnabled.collectAsState()
    val uriHandler = LocalUriHandler.current

    BlurHeaderBox(
        title = stringResource(R.string.plugin_settings_title),
        onBack = { navController.popBackStack() },
        accentColor = MaterialTheme.colorScheme.primary,
    ) { headerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = headerPadding + 8.dp, bottom = 140.dp),
        ) {
            item("sec_header") {
                SectionLabel(stringResource(R.string.plugin_security_section))
            }
            item("ask_download") {
                MaterialCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    accentColor = MaterialTheme.colorScheme.primary,
                    contentPadding = 16.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                stringResource(R.string.plugin_ask_download_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.plugin_ask_download_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DolphySwitch(checked = ask, onCheckedChange = PluginDownloadPolicy::setAskBeforeDownload)
                    }
                }
            }
            MaterialButton(
                text = stringResource(R.string.plugin_documentation_site),
                onClick = { uriHandler.openUri("https://zitk9.github.io/Deadcow-App/") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
