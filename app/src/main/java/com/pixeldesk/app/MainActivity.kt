package com.pixeldesk.app

import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
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
        EnvironmentRuntimeManager.resume()
        setContent { PixelDeskApp() }
    }

    override fun onDestroy() {
        EnvironmentRuntimeManager.stopAll()
        super.onDestroy()
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
                        Triple("Files", Icons.Rounded.Folder, 2),
                        Triple("Terminal", Icons.Rounded.Computer, 3),
                        Triple("Apps", Icons.Rounded.Apps, 4),
                        Triple("Settings", Icons.Rounded.Settings, 5)
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
                    3 -> LinuxTerminalScreen()
                    4 -> LinuxSoftwareScreen()
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
    val scope = rememberCoroutineScope()
    val lifecycle = remember(context) { EnvironmentLifecycleController(context) }
    var workspaces by remember { mutableStateOf(WorkspaceStorage.list(context)) }
    var message by remember { mutableStateOf("Debian 12 ARM64 is ready to install.") }
    var isInstalling by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<EnvironmentWorkspace?>(null) }
    var pendingExportWorkspace by remember { mutableStateOf<EnvironmentWorkspace?>(null) }
    val installedWorkspace = remember(workspaces, refresh) {
        workspaces.firstOrNull { workspace ->
            runCatching { RootfsValidator.validate(workspace.rootFilesystem) }.isSuccess
        }
    }

    fun reload() {
        workspaces = WorkspaceStorage.list(context)
        refresh++
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        val workspaceToExport = pendingExportWorkspace
        pendingExportWorkspace = null
        if (uri == null) {
            message = "Backup export cancelled."
        } else if (workspaceToExport != null) {
            scope.launch {
                isBusy = true
                message = "Creating a compressed backup of ${workspaceToExport.displayName}…"
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: error("Could not open the selected backup destination.")
                        stream.use { output ->
                            EnvironmentBackupExporter.export(workspaceToExport, output)
                        }
                    }
                }
                result.onSuccess { backup ->
                    message = "Backup saved · ${backup.entryCount} entries · ${backup.uncompressedBytes.toReadableSize()}\nSHA-256: ${backup.sha256}"
                }.onFailure { error ->
                    message = error.message ?: "Backup export failed. Remove any incomplete file and retry."
                }
                isBusy = false
            }
        }
    }

    PageScaffold("Environments", "Back up, start, stop and manage local Debian workspaces.") {
        FeatureCard(
            "Debian 12 Bookworm · ARM64",
            "Pinned official rootfs layer · HTTPS download · SHA-256 verification.",
            if (installedWorkspace != null) "ROOTFS INSTALLED" else "READY TO INSTALL",
            if (installedWorkspace != null) Cyan else Violet
        )
        Button(
            onClick = {
                scope.launch {
                    isInstalling = true
                    isBusy = true
                    message = "Downloading and verifying the pinned Debian rootfs…"
                    try {
                        val workspace = WorkspaceStorage.list(context)
                            .firstOrNull { it.id == "debian-arm64" }
                            ?: WorkspaceStorage.create(context, "Debian ARM64")
                        val result = lifecycle.install(workspace)
                        message = "Installed Debian ${result.validation.release} ARM64 · ${result.expandedBytes.toReadableSize()} expanded."
                        reload()
                    } catch (error: Exception) {
                        message = error.message ?: "Debian installation failed."
                    } finally {
                        isInstalling = false
                        isBusy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy && installedWorkspace == null
        ) { Text(if (isInstalling) "Installing Debian…" else if (installedWorkspace != null) "Debian already installed" else "Install Debian 12 ARM64") }

        Text(message, color = Muted, fontSize = 12.sp)
        workspaces.forEach { workspace ->
            val state = lifecycle.state(workspace)
            val running = state == EnvironmentState.RUNNING
            val valid = state == EnvironmentState.INSTALLED || running
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(workspace.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (state) {
                            EnvironmentState.STORAGE_ONLY -> "Workspace folders exist; Debian root filesystem is not installed."
                            EnvironmentState.INSTALLED -> "Debian root filesystem verified and stopped."
                            EnvironmentState.RUNNING -> "Debian shell process is running."
                            EnvironmentState.INSTALLING -> "Debian installation is in progress."
                            EnvironmentState.STARTING -> "Starting Debian…"
                            EnvironmentState.STOPPING -> "Stopping Debian…"
                            EnvironmentState.INVALID -> "Root filesystem is missing or failed validation."
                        },
                        color = Muted,
                        fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    try {
                                        if (running) {
                                            lifecycle.stop(workspace)
                                            message = "Stopped ${workspace.displayName}."
                                        } else {
                                            lifecycle.start(workspace)
                                            EnvironmentRuntimeManager.select(workspace.id)
                                            message = "Started ${workspace.displayName}. Open Terminal to use the persistent shell."
                                        }
                                        reload()
                                    } catch (error: Exception) {
                                        message = error.message ?: "Environment action failed."
                                    } finally { isBusy = false }
                                }
                            },
                            enabled = !isBusy && valid
                        ) { Text(if (running) "Stop" else "Start") }
                        TextButton(
                            onClick = {
                                pendingExportWorkspace = workspace
                                exportLauncher.launch("${workspace.id}-backup.tar.gz")
                            },
                            enabled = !isBusy && !running && state != EnvironmentState.INSTALLING
                        ) { Text("Export backup") }
                        TextButton(
                            onClick = { pendingDelete = workspace },
                            enabled = !isBusy && state != EnvironmentState.INSTALLING
                        ) { Text("Delete") }
                    }
                }
            }
        }
        FeatureCard("Windows compatibility", "Wine and CPU translation remain experimental and are not enabled.", "EXPERIMENTAL", Cyan)
        FeatureCard("Virtual machines", "QEMU feasibility research; device support and performance are not yet verified.", "RESEARCH", Muted)
    }

    pendingDelete?.let { workspace ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${workspace.displayName}?") },
            text = { Text("This permanently removes its Debian root filesystem, installed packages, home files, downloads and temporary data from PIXEL DESK. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        isBusy = true
                        try {
                            lifecycle.delete(workspace)
                            message = "Deleted ${workspace.displayName} and its local data."
                            reload()
                        } catch (error: Exception) {
                            message = error.message ?: "Could not delete the environment."
                        } finally { isBusy = false }
                    }
                }) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LibraryScreen() {
    val context = LocalContext.current
    val workspace = remember { WorkspaceStorage.list(context).firstOrNull() }
    val scope = rememberCoroutineScope()
    var currentPath by rememberSaveable { mutableStateOf("") }
    var itemName by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Create folders and text files inside this private workspace.") }
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && workspace != null) {
            message = "Importing selected file…"
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val displayName = context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: "imported-file"
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("Could not open selected file.")
                        WorkspaceImport.copyStream(
                            root = workspace.directory,
                            parentPath = "downloads",
                            displayName = displayName,
                            input = input
                        )
                    }
                }
                result.onSuccess { imported ->
                    currentPath = "downloads"
                    refresh++
                    message = "Imported ${imported.file.name} (${imported.bytesWritten.toReadableSize()}) into downloads."
                }.onFailure { error ->
                    message = error.message ?: "Import failed."
                }
            }
        }
    }
    val entries = remember(workspace, currentPath, refresh) {
        workspace?.let { runCatching { WorkspaceFiles.list(it.directory, currentPath) }.getOrDefault(emptyList()) }
            .orEmpty()
    }

    PageScaffold("Workspace files", "Browse local files stored inside PIXEL DESK.") {
        if (workspace == null) {
            EmptyState(Icons.Rounded.Folder, "No workspace yet", "Prepare a Debian workspace in Environments first.")
        } else {
            Text("PRIVATE WORKSPACE  /  ${if (currentPath.isBlank()) "root" else currentPath}", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = { importPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Import file into downloads") }
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
    val runtimeFiles = remember { ProotRuntimeProbe.locate(context) }
    val scope = rememberCoroutineScope()
    var runtimeMessage by remember { mutableStateOf("Run a native PRoot check to verify the bundled runtime.") }
    var checkingRuntime by remember { mutableStateOf(false) }
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
        Text("NATIVE LINUX RUNTIME", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        FeatureCard(
            "PRoot ARM64",
            if (runtimeFiles != null) "Native executable and loader are present in Android's native library directory."
            else "Native payload is missing or this device ABI is unsupported.",
            if (runtimeFiles != null) "PAYLOAD FOUND" else "NOT AVAILABLE",
            if (runtimeFiles != null) Cyan else Muted
        )
        Button(
            onClick = {
                scope.launch {
                    checkingRuntime = true
                    val result = ProotRuntimeProbe.check(context)
                    runtimeMessage = result.message + (result.exitCode?.let { " (exit $it)" } ?: "")
                    checkingRuntime = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !checkingRuntime
        ) {
            Text(if (checkingRuntime) "Checking native runtime…" else "Test PRoot executable")
        }
        Text(runtimeMessage, color = Muted, fontSize = 12.sp)
        Text(
            "This probe checks PRoot startup only. It does not install Linux or run desktop applications.",
            color = Muted,
            fontSize = 10.sp
        )
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
