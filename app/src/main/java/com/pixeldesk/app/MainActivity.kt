package com.pixeldesk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF090B12)
private val Panel = Color(0xFF151A28)
private val Violet = Color(0xFF9B8CFF)
private val Cyan = Color(0xFF67DCE8)
private val Muted = Color(0xFF9AA4B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PixelDeskApp() }
    }
}

@Composable
private fun PixelDeskApp() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    MaterialTheme {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Color.White) {
                    val items = listOf(
                        Triple("Home", Icons.Rounded.Home, 0),
                        Triple("Environments", Icons.Rounded.Computer, 1),
                        Triple("Library", Icons.Rounded.Apps, 2),
                        Triple("Settings", Icons.Rounded.Settings, 3)
                    )
                    items.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selectedTab) {
                    0 -> HomeScreen()
                    1 -> EnvironmentsScreen()
                    2 -> LibraryScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val device = remember { DeviceSnapshot.read(context) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text("PIXEL DESK", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text("Your pocket workstation.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text("A desktop environment, built for Android.", color = Muted, fontSize = 14.sp)
        }
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.background(
                        Brush.linearGradient(listOf(Color(0xFF29234C), Color(0xFF172D3B), Panel))
                    ).padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(Violet.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Computer, contentDescription = null, tint = Violet)
                            }
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text("LOCAL WORKSPACE", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Text("Ready to be configured", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text(
                            "PIXEL DESK is being built around local ARM64 Linux first, with Windows compatibility as an experimental layer.",
                            color = Color(0xFFD0D5E2), fontSize = 13.sp, lineHeight = 19.sp
                        )
                        Surface(color = Color(0xFF3A315F), shape = RoundedCornerShape(50)) {
                            Text("FOUNDATION BUILD  •  v0.1.0", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Text("DEVICE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCard("DEVICE", device.model, Modifier.weight(1f))
                InfoCard("ANDROID", device.androidVersion + " · API " + device.apiLevel, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCard("RAM AVAILABLE", device.availableRamBytes.toReadableSize(), Modifier.weight(1f))
                InfoCard("STORAGE FREE", device.availableStorageBytes.toReadableSize(), Modifier.weight(1f))
            }
            Text("WORKSPACES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            FeatureCard("Linux ARM64", "First target · local userspace", "IN DEVELOPMENT", Violet)
            FeatureCard("Windows compatibility", "Wine + CPU translation research", "EXPERIMENTAL", Cyan)
            FeatureCard("Virtual machines", "QEMU feasibility research", "RESEARCH", Muted)
        }
        item {
            Text("Designed to run locally. No account or cloud runtime is required.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 20.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, status: String, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(accent, CircleShape))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Text(status, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EnvironmentsScreen() {
    val context = LocalContext.current
    var workspaces by remember { mutableStateOf(WorkspaceStorage.list(context)) }
    var message by remember { mutableStateOf("No local workspace has been prepared yet.") }

    PageScaffold("Environments", "Prepare local storage before installing a Linux userspace.") {
        FeatureCard(
            "Linux ARM64",
            "Debian-based userspace is the first implementation target.",
            if (workspaces.isEmpty()) "NEXT STEP" else "STORAGE READY",
            Violet
        )
        Button(
            onClick = {
                runCatching {
                    WorkspaceStorage.create(context, "Debian ARM64")
                }.onSuccess { workspace ->
                    workspaces = WorkspaceStorage.list(context)
                    message = "Workspace prepared at app-private storage: ${workspace.id}"
                }.onFailure { error ->
                    message = error.message ?: "Could not prepare workspace."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (workspaces.isEmpty()) "Prepare Debian workspace" else "Repair / prepare workspace")
        }
        Text(message, color = Muted, fontSize = 12.sp)
        workspaces.forEach { workspace ->
            FeatureCard(
                workspace.displayName,
                "Private storage folders are ready. Linux is not installed yet.",
                "STORAGE ONLY",
                Cyan
            )
        }
        FeatureCard("Windows compatibility", "Wine and translation layers; application compatibility varies.", "EXPERIMENTAL", Cyan)
        FeatureCard("Virtual machines", "QEMU research; device support and performance are not yet verified.", "RESEARCH", Muted)
    }
}

@Composable
private fun LibraryScreen() {
    val context = LocalContext.current
    val workspace = remember { WorkspaceStorage.list(context).firstOrNull() }
    var currentPath by rememberSaveable { mutableStateOf("") }
    var itemName by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Create folders and text files inside this private workspace.") }
    val entries = remember(workspace, currentPath, refresh) {
        workspace?.let { runCatching { WorkspaceFiles.list(it.directory, currentPath) }.getOrDefault(emptyList()) }
            .orEmpty()
    }

    PageScaffold("Workspace files", "Browse local files stored inside PIXEL DESK.") {
        if (workspace == null) {
            EmptyState(Icons.Rounded.Folder, "No workspace yet", "Prepare a Debian workspace in Environments first.")
        } else {
            Text("PRIVATE WORKSPACE  /  ${if (currentPath.isBlank()) "root" else currentPath}", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (currentPath.isNotBlank()) {
                TextButton(onClick = {
                    currentPath = currentPath.substringBeforeLast('/', "")
                    message = "Opened parent folder."
                }) { Text("← Parent folder") }
            }
            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                label = { Text("New item name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching { WorkspaceFiles.createDirectory(workspace.directory, currentPath, itemName.trim()) }
                        .onSuccess { itemName = ""; refresh++; message = "Folder created." }
                        .onFailure { message = it.message ?: "Could not create folder." }
                }, modifier = Modifier.weight(1f), enabled = itemName.isNotBlank()) { Text("New folder") }
                Button(onClick = {
                    runCatching { WorkspaceFiles.createTextFile(workspace.directory, currentPath, itemName.trim()) }
                        .onSuccess { itemName = ""; refresh++; message = "Text file created." }
                        .onFailure { message = it.message ?: "Could not create file." }
                }, modifier = Modifier.weight(1f), enabled = itemName.isNotBlank()) { Text("New file") }
            }
            Text(message, color = Muted, fontSize = 11.sp)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries.size) { index ->
                    val entry = entries[index]
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Panel, RoundedCornerShape(12.dp))
                            .clickable {
                                if (entry.isDirectory) {
                                    currentPath = entry.relativePath
                                    message = "Opened ${entry.name}"
                                } else {
                                    message = runCatching {
                                        WorkspaceFiles.readText(workspace.directory, entry.relativePath)
                                    }.getOrElse { it.message ?: "Preview unavailable." }
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = if (entry.isDirectory) Violet else Cyan
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (entry.isDirectory) "Folder" else entry.sizeBytes.toReadableSize(), color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
            Text("This is a basic workspace file browser, not a Linux filesystem yet.", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val device = remember { DeviceSnapshot.read(context) }
    PageScaffold("Settings", "PIXEL DESK preferences and device diagnostics.") {
        FeatureCard("Privacy first", "Environment files stay on-device by default.", "LOCAL", Cyan)
        FeatureCard("No root required", "The planned basic Linux environment targets unrooted Android.", "DESIGN", Violet)
        Text("DEVICE COMPATIBILITY", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        InfoCard("PRIMARY ABI", device.primaryAbi, Modifier.fillMaxWidth())
        FeatureCard(
            "ARM64 runtime",
            "Supported ABIs: ${device.supportedAbis.joinToString().ifBlank { "Unknown" }}",
            if (device.supportsArm64) "ABI DETECTED" else "NOT SUPPORTED",
            if (device.supportsArm64) Cyan else Muted
        )
        InfoCard("TOTAL RAM", device.totalRamBytes.toReadableSize(), Modifier.fillMaxWidth())
        InfoCard("TOTAL DATA STORAGE", device.totalStorageBytes.toReadableSize(), Modifier.fillMaxWidth())
        FeatureCard("Compatibility", "Windows and VM support will be reported per device and application.", "HONEST STATUS", Muted)
    }
}

@Composable
private fun PageScaffold(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
        content()
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Violet, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(body, color = Muted, fontSize = 12.sp)
        }
    }
}
