package com.pixeldesk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun LinuxSoftwareScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workspace = remember {
        val available = WorkspaceStorage.list(context).filter { candidate ->
            runCatching { RootfsValidator.validate(candidate.rootFilesystem) }.isSuccess
        }
        val selectedId = EnvironmentRuntimeManager.selectedEnvironmentId()
        available.firstOrNull { it.id == selectedId } ?: available.firstOrNull()
    }
    var session by remember { mutableStateOf<ProotInteractiveSession?>(null) }
    var packageNames by rememberSaveable { mutableStateOf("nano curl") }
    var output by remember { mutableStateOf("PIXEL DESK software manager\nPreparing Debian package tools…") }
    var status by remember { mutableStateOf("STARTING") }
    var networkStatus by remember { mutableStateOf("Checking Android network DNS…") }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(workspace) {
        if (workspace == null) {
            status = "NOT INSTALLED"
            networkStatus = "Install Debian before managing packages."
            output = "No verified Debian environment was found."
        } else {
            try {
                val dns = withContext(Dispatchers.IO) {
                    AndroidDnsConfigurator.configure(context, workspace.rootFilesystem)
                }
                networkStatus = if (dns.configured) {
                    "${dns.message} Nameservers: ${dns.nameservers.joinToString()}"
                } else {
                    dns.message + " Package downloads may fail until the device is online."
                }
                session = EnvironmentRuntimeManager.start(context, workspace)
                status = "CONNECTED"
                output = "Connected to ${workspace.displayName}.\nRefresh package indexes before installing software."
            } catch (error: Exception) {
                status = "CONNECTION ERROR"
                output = error.message ?: "Could not prepare Debian package management."
            }
        }
    }


    fun runOperation(operation: PackageOperation) {
        val activeSession = session ?: return
        scope.launch {
            running = true
            status = operation.title.uppercase()
            output = "$ ${operation.command}\nWorking…"
            try {
                val result = LinuxPackageManager.run(activeSession, operation)
                output = result.output + "\n\nExit: ${result.exitCode ?: "unknown"} · ${result.durationMs} ms"
                status = if (result.success) "COMPLETED" else if (result.timedOut) "TIMED OUT" else "FAILED"
                if (!result.sessionAlive) session = null
            } catch (error: Exception) {
                output = error.message ?: "Package operation failed."
                status = "FAILED"
            } finally {
                running = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090B12)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Software Manager", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Install and remove Debian ARM64 packages locally.", color = Color(0xFF9AA4B8), fontSize = 13.sp)
            Text("${workspace?.displayName ?: "No Debian environment"} · $status", color = Color(0xFF67DCE8), fontWeight = FontWeight.SemiBold)
            Text(networkStatus, color = Color(0xFF9AA4B8), fontSize = 11.sp)
            Button(
                onClick = { runOperation(LinuxPackageManager.updateOperation()) },
                enabled = session != null && !running,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (running) "Working…" else "Refresh package indexes") }

            OutlinedTextField(
                value = packageNames,
                onValueChange = { packageNames = it },
                label = { Text("Package names") },
                placeholder = { Text("nano curl python3") },
                supportingText = { Text("Use Debian package names separated by spaces; maximum 20 per operation.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        runCatching { LinuxPackageManager.installOperation(packageNames) }
                            .onSuccess { operation -> runOperation(operation) }
                            .onFailure { output = it.message ?: "Invalid package names."; status = "INVALID INPUT" }
                    },
                    enabled = session != null && !running,
                    modifier = Modifier.weight(1f)
                ) { Text("Install") }
                TextButton(
                    onClick = {
                        runCatching { LinuxPackageManager.removeOperation(packageNames) }
                            .onSuccess(::runOperation)
                            .onFailure { output = it.message ?: "Invalid package names."; status = "INVALID INPUT" }
                    },
                    enabled = session != null && !running
                ) { Text("Remove") }
            }
            TextButton(
                onClick = { runOperation(LinuxPackageManager.cleanOperation()) },
                enabled = session != null && !running
            ) { Text("Clean package cache") }

            if (session == null && workspace != null && !running) {
                TextButton(onClick = {
                    scope.launch {
                        status = "CONNECTING"
                        try {
                            val dns = withContext(Dispatchers.IO) {
                                AndroidDnsConfigurator.configure(context, workspace.rootFilesystem)
                            }
                            networkStatus = dns.message
                            session = EnvironmentRuntimeManager.start(context, workspace)
                            status = "CONNECTED"
                        } catch (error: Exception) {
                            status = "CONNECTION ERROR"
                            output = error.message ?: "Could not reconnect."
                        }
                    }
                }) { Text("Reconnect Debian") }
            }

            Text("OPERATION OUTPUT", color = Color(0xFF9AA4B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Column(
                Modifier.fillMaxWidth()
                    .height(300.dp)
                    .background(Color(0xFF05070C), RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(output, color = Color(0xFFD6E3E8), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 17.sp)
            }
            Text(
                "Package installation uses Debian APT and the device's active network. Only validated package names are passed to APT. Package scripts execute inside the PRoot userspace and app sandbox.",
                color = Color(0xFF9AA4B8),
                fontSize = 10.sp
            )
        }
    }
}
