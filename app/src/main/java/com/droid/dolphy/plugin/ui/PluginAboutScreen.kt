package com.droid.dolphy.plugin.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.BlurHeaderBox
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R

@Composable
fun PluginAboutScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary
<<<<<<< HEAD
    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = stringResource(R.string.plugin_about_title),
                onBack = { navController.popBackStack() },
                accentColor = accent,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            ) {
                item {
                    GuideCard(
                        icon = Icons.Default.Code,
                        title = stringResource(R.string.plugin_about_guide_title),
                        body = stringResource(R.string.plugin_about_guide_body),
                        accent = accent,
                    )
                }
                item { SectionTitle("Plugin minimal") }
                item {
                    CodeBlock(
                        """
                        __id__ = "radio_lab"
                        __name__ = "Radio Lab"
                        __version__ = "1.0.0"
                        __author__ = "Developer"
                        __description__ = "Bluetooth and Wi-Fi toolkit"
                        __icon__ = "filled:bluetooth_searching"

                        class RadioLab(BasePlugin):
                            def on_plugin_load(self):
                                self.add_module(
                                    title="Radio Lab",
                                    description="Wireless diagnostics",
                                    icon="bluetooth",
                                    section="BLUETOOTH"
                                )

                            def screen_main(self, ui, api, state):
                                return ui.scaffold(
                                    title="Radio Lab",
                                    content=ui.column([
                                        ui.card([
                                            ui.text("Bluetooth", "titleLarge"),
                                            ui.button("Scan", self.scan, fill_max_width=True)
                                        ])
                                    ], padding=16, spacing=12, fill_max_size=True)
                                )

                            def scan(self):
                                self.toast("Starting scan")
                        """.trimIndent(),
                    )
                }
                item { SectionTitle("Accès complet") }
                item {
                    GuideCard(
                        icon = Icons.Default.Terminal,
                        title = "Android, Root et Shizuku",
                        body = "api.getContext(), api.getApplicationContext(), api.getClassLoader(), api.getAndroid() et api.getDevice() retournent les objets Java réels de Deadcow. Les commandes système sont exécutables via api.shellExec(), api.rootExec(), api.shizukuExec() et api.smartExec().",
                        accent = accent,
                    )
                }
                item {
                    CodeBlock(
                        """
                        from java import jclass

                        BluetoothAdapter = jclass("android.bluetooth.BluetoothAdapter")
                        adapter = BluetoothAdapter.getDefaultAdapter()
                        result = self.root("id; getenforce; ls /sys/class/udc")
                        context = self.api.getContext()
                        app_class = self.api.getClassLoader().loadClass(
                            "com.droid.dolphy.MainActivity"
                        )
                        """.trimIndent(),
                    )
                }
                item { SectionTitle("Interfaces Radio") }
                item {
                    GuideCard(
                        icon = Icons.Default.Bluetooth,
                        title = "Bluetooth et BLE",
                        body = "Via api.getAndroid() : advertising, clients/serveurs GATT, RFCOMM, appairage et état de l'adaptateur. api.getDevice() fournit le scan BLE, les fonctionnalités matérielles et les opérations système.",
                        accent = accent,
                    )
                }
                item {
                    GuideCard(
                        icon = Icons.Default.Wifi,
                        title = "Wi-Fi et Réseau",
                        body = "Résultats de scans Wi-Fi, interfaces, baux DHCP, canaux, NSD, sockets, HTTP et accès direct à Android WifiManager. Root et Shizuku permettent d'utiliser les utilitaires réseau natifs de l'OS.",
                        accent = accent,
                    )
                }
                item { SectionTitle("Interception & Hooks") }
                item {
                    GuideCard(
                        icon = Icons.Default.Code,
                        title = "Écrans, actions et services",
                        body = "hook_screen() ajoute une surcouche, un bouton FAB ou remplace un écran officiel. hook_action() intercepte les opérations nommées. provide_service() injecte une implémentation de service système avec priorité et repli automatique.",
                        accent = accent,
                    )
                }
                item {
                    CodeBlock(
                        """
=======
    BlurHeaderBox(
        title = stringResource(R.string.plugin_about_title),
        onBack = { navController.popBackStack() },
        accentColor = accent,
    ) { headerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = headerPadding + 8.dp, bottom = 120.dp),
        ) {
            item {
                GuideCard(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.plugin_about_guide_title),
                    body = stringResource(R.string.plugin_about_guide_body),
                    accent = accent,
                )
            }
            item { SectionTitle(stringResource(R.string.pabout_section_minimal)) }
            item {
                CodeBlock(
                    """
                    __id__ = "radio_lab"
                    __name__ = "Radio Lab"
                    __version__ = "1.0.0"
                    __author__ = "Developer"
                    __description__ = "Bluetooth and Wi-Fi toolkit"
                    __icon__ = "filled:bluetooth_searching"

                    class RadioLab(BasePlugin):
>>>>>>> upstream/main
                        def on_plugin_load(self):
                            self.add_module(
                                title="Radio Lab",
                                description="Wireless diagnostics",
                                icon="bluetooth",
                                section="BLUETOOTH"
                            )
<<<<<<< HEAD
                            return {"handled": True, "ok": ok}
                        """.trimIndent(),
                    )
                }
                item {
                    GuideCard(
                        icon = Icons.Default.Security,
                        title = "Modes de hooks d'écrans",
                        body = "replace remplace l'écran, overlay dessine par-dessus, top et bottom ajoutent une barre Material 3, fab ajoute une action flottante. Les masques de routes acceptent le joker *, par ex. ir_* ou other/*.",
                        accent = accent,
                    )
                }
                item { SectionTitle("État et Paramètres") }
                item {
                    CodeBlock(
                        """
                        enabled = self.get_setting("enabled", True)
                        self.set_setting("enabled", not enabled)
=======
>>>>>>> upstream/main

                        def screen_main(self, ui, api, state):
                            return ui.scaffold(
                                title="Radio Lab",
                                content=ui.column([
                                    ui.card([
                                        ui.text("Bluetooth", "titleLarge"),
                                        ui.button("Scan", self.scan, fill_max_width=True)
                                    ])
                                ], padding=16, spacing=12, fill_max_size=True)
                            )

                        def scan(self):
                            self.toast("Starting scan")
                    """.trimIndent(),
                )
            }
            item { SectionTitle(stringResource(R.string.pabout_section_full_access)) }
            item {
                GuideCard(
                    icon = Icons.Default.Terminal,
                    title = stringResource(R.string.pabout_title_root),
                    body = stringResource(R.string.pabout_body_root),
                    accent = accent,
                )
            }
            item {
                CodeBlock(
                    """
                    from java import jclass

                    BluetoothAdapter = jclass("android.bluetooth.BluetoothAdapter")
                    adapter = BluetoothAdapter.getDefaultAdapter()
                    result = self.root("id; getenforce; ls /sys/class/udc")
                    context = self.api.getContext()
                    app_class = self.api.getClassLoader().loadClass(
                        "com.droid.dolphy.MainActivity"
                    )
                    """.trimIndent(),
                )
            }
            item { SectionTitle(stringResource(R.string.pabout_section_radio)) }
            item {
                GuideCard(
                    icon = Icons.Default.Bluetooth,
                    title = stringResource(R.string.pabout_title_bluetooth),
                    body = stringResource(R.string.pabout_body_bluetooth),
                    accent = accent,
                )
            }
            item {
                GuideCard(
                    icon = Icons.Default.Wifi,
                    title = stringResource(R.string.pabout_title_wifi),
                    body = stringResource(R.string.pabout_body_wifi),
                    accent = accent,
                )
            }
            item { SectionTitle(stringResource(R.string.pabout_section_hooking)) }
            item {
                GuideCard(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.pabout_title_hooks),
                    body = stringResource(R.string.pabout_body_hooks),
                    accent = accent,
                )
            }
            item {
                CodeBlock(
                    """
                    def on_plugin_load(self):
                        self.hook_screen("ir_*", "ir_tools", "fab", 50)
                        self.hook_action("infrared.transmit", 50)
                        self.provide_service("infrared.transmitter", 100)

                    def screen_ir_tools(self, ui, api, state):
                        return ui.icon_button("usb", self.open_usb)

                    def hook_infrared_transmit(self, payload):
                        payload["source"] = "plugin"
                        return {"payload": payload}

                    def service_infrared_transmitter_available(self, payload):
                        return {"available": self.usb_connected()}

                    def service_infrared_transmitter_transmit(self, payload):
                        if not self.usb_connected():
                            return {"handled": False}
                        ok = self.send_usb(
                            payload["frequency"],
                            payload["pattern"]
                        )
                        return {"handled": True, "ok": ok}
                    """.trimIndent(),
                )
            }
            item {
                GuideCard(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.pabout_title_hook_modes),
                    body = stringResource(R.string.pabout_body_hook_modes),
                    accent = accent,
                )
            }
            item { SectionTitle(stringResource(R.string.pabout_section_state)) }
            item {
                CodeBlock(
                    """
                    enabled = self.get_setting("enabled", True)
                    self.set_setting("enabled", not enabled)

                    self.api.registerSettings(
                        "Radio Lab",
                        '[{"type":"switch","key":"enabled","title":"Enabled","default":true}]'
                    )
<<<<<<< HEAD
                }
                item {
                    GuideCard(
                        icon = Icons.Default.Security,
                        title = "Modèle de sécurité de confiance",
                        body = "Les plugins Python s'exécutent au sein du processus Deadcow et disposent des permissions de l'application, des données internes, du Root et de Shizuku. N'installez que du code audité ou en qui vous avez totale confiance.",
                        accent = accent,
                    )
                }
=======
                    """.trimIndent(),
                )
            }
            item {
                GuideCard(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.pabout_title_trusted),
                    body = stringResource(R.string.pabout_body_trusted),
                    accent = accent,
                )
>>>>>>> upstream/main
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun GuideCard(icon: ImageVector, title: String, body: String, accent: Color) {
    MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = accent)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = code,
            modifier = Modifier.padding(16.dp).horizontalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
