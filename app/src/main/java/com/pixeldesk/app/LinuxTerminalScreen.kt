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
import androidx.compose.runtime.DisposableEffect
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

@Composable
fun LinuxTerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workspace = remember {
        WorkspaceStorage.list(context).firstOrNull { candidate ->
            runCatching { RootfsValidator.validate(candidate.rootFilesystem) }.isSuccess
        }
    }
    var session by remember { mutableStateOf<ProotInteractiveSession?>(null) }
    var command by rememberSaveable { mutableStateOf("uname -m") }
    var output by remember { mutableStateOf("PIXEL DESK terminal\nPreparing local Debian shell…") }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("STARTING") }
    var history by rememberSaveable { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(workspace) {
        if (workspace == null) {
            status = "NOT INSTALLED"
            output = "Install Debian 12 ARM64 from Environments before opening the terminal."
        } else {
            try {
                session = ProotInteractiveSession.start(context, workspace)
                status = "CONNECTED"
                output = "Connected to \${workspace.displayName}\nTry: uname -m"
            } catch (error: Exception) {
                status = "CONNECTION ERROR"
                output = error.message ?: "Could not start the Debian shell."
            }
        }
    }

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090B12)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Linux Terminal", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Persistent local Debian ARM64 shell", color = Color(0xFF9AA4B8), fontSize = 13.sp)
            Text(
                "\${workspace?.displayName ?: "No verified Debian environment"} · $status",
                color = if (session != null) Color(0xFF67DCE8) else Color(0xFF9AA4B8),
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("Linux command") },
                placeholder = { Text("uname -m") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 4
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val activeSession = session ?: return@Button
                        val submitted = command.trim()
                        scope.launch {
                            running = true
                            history = (history.filterNot { it == submitted } + submitted).takeLast(50)
                            try {
                                val result = activeSession.execute(submitted)
                                val statusText = if (result.timedOut) "TIMED OUT"
                                    else "EXIT \${result.exitCode ?: "UNKNOWN"}"
                                val addition = "$ $submitted\n\${result.output}\n$statusText · \${result.durationMs} ms\n"
                                output = (output + "\n" + addition).takeLast(65_536)
                                status = if (result.sessionAlive) "CONNECTED" else "DISCONNECTED"
                                if (!result.sessionAlive) session = null
                            } catch (error: Exception) {
                                output = (output + "\n" + (error.message ?: "Command failed.")).takeLast(65_536)
                                status = "COMMAND ERROR"
                            } finally {
                                running = false
                            }
                        }
                    },
                    enabled = session != null && !running && command.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (running) "Running…" else "Run")
                }
                TextButton(
                    onClick = {
                        session?.close()
                        session = null
                        status = "DISCONNECTED"
                    },
                    enabled = session != null && !running
                ) { Text("Stop") }
                TextButton(onClick = {
                    output = "PIXEL DESK terminal\nOutput cleared."
                }) { Text("Clear") }
            }
            if (session == null && workspace != null && !running) {
                TextButton(onClick = {
                    scope.launch {
                        status = "CONNECTING"
                        try {
                            session = ProotInteractiveSession.start(context, workspace)
                            status = "CONNECTED"
                            output += "\nShell reconnected."
                        } catch (error: Exception) {
                            status = "CONNECTION ERROR"
                            output += "\n\${error.message ?: "Could not reconnect."}"
                        }
                    }
                }) { Text("Reconnect shell") }
            }
            if (history.isNotEmpty()) {
                Text("RECENT COMMANDS", color = Color(0xFF9AA4B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                history.takeLast(5).asReversed().forEach { previous ->
                    TextButton(onClick = { command = previous }) {
                        Text(previous.take(72), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
            Text(status, color = Color(0xFF67DCE8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Column(
                Modifier.fillMaxWidth()
                    .height(320.dp)
                    .background(Color(0xFF05070C), RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    output,
                    color = Color(0xFFD6E3E8),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
            }
            Text(
                "Shell variables and working-directory changes persist between commands. This pipe-based terminal does not yet support full-screen interactive programs or job control.",
                color = Color(0xFF9AA4B8),
                fontSize = 10.sp
            )
        }
    }
}
