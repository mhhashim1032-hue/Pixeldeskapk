package com.pixeldesk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LinuxTerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val workspace = remember {
        val available = WorkspaceStorage.list(context).filter { candidate ->
            runCatching { RootfsValidator.validate(candidate.rootFilesystem) }.isSuccess
        }
        val selectedId = EnvironmentRuntimeManager.selectedEnvironmentId()
        available.firstOrNull { it.id == selectedId } ?: available.firstOrNull()
    }
    var session by remember { mutableStateOf<ProotPtySession?>(null) }
    var command by rememberSaveable { mutableStateOf("") }
    var output by remember { mutableStateOf("PIXEL DESK terminal\nStarting PTY-backed Debian shell…\n") }
    var status by remember { mutableStateOf("STARTING") }
    var history by rememberSaveable { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(workspace) {
        if (workspace == null) {
            status = "NOT INSTALLED"
            output = "Install Debian 12 ARM64 from Environments before opening the terminal."
        } else {
            try {
                session = EnvironmentRuntimeManager.startPty(context, workspace)
                status = "CONNECTED · PTY"
            } catch (error: Exception) {
                status = "CONNECTION ERROR"
                output = error.message ?: "Could not start the Debian PTY shell."
            }
        }
    }

    LaunchedEffect(session) {
        val active = session ?: return@LaunchedEffect
        try {
            while (active.isAlive) {
                val bytes = active.readOutput() ?: break
                output = (output + bytes.toString(Charsets.UTF_8)).takeLast(65_536)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Session termination is reflected in the status below.
        } finally {
            if (session === active) {
                status = "DISCONNECTED"
                session = null
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090B12)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Linux Terminal", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Native POSIX PTY · Debian ARM64", color = Color(0xFF9AA4B8), fontSize = 13.sp)
            Text(
                "${workspace?.displayName ?: "No verified Debian environment"} · $status",
                color = if (session != null) Color(0xFF67DCE8) else Color(0xFF9AA4B8),
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("Terminal input") },
                placeholder = { Text("Type a command and press Send") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 4
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val active = session ?: return@Button
                        val submitted = command
                        if (submitted.isNotEmpty()) {
                            runCatching { active.writeText(submitted + "\n") }
                                .onFailure { output += "\nInput error: ${it.message}" }
                            history = (history.filterNot { it == submitted } + submitted).takeLast(50)
                            command = ""
                        }
                    },
                    enabled = session != null && command.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Send") }
                TextButton(
                    onClick = {
                        val sent = session?.interrupt() == true
                        if (!sent) output += "\nCould not send Ctrl+C to the PTY process group.\n"
                    },
                    enabled = session != null
                ) { Text("Ctrl+C") }
                TextButton(
                    onClick = {
                        workspace?.let { target ->
                            scope.launch {
                                EnvironmentRuntimeManager.stop(target.id)
                                session = null
                                status = "DISCONNECTED"
                            }
                        }
                    },
                    enabled = session != null
                ) { Text("Stop") }
                TextButton(onClick = { output = "" }) { Text("Clear") }
            }
            if (session == null && workspace != null) {
                TextButton(onClick = {
                    scope.launch {
                        status = "CONNECTING"
                        try {
                            session = EnvironmentRuntimeManager.startPty(context, workspace)
                            status = "CONNECTED · PTY"
                        } catch (error: Exception) {
                            status = "CONNECTION ERROR"
                            output += "\n${error.message ?: "Could not reconnect."}"
                        }
                    }
                }) { Text("Reconnect terminal") }
            }
            if (history.isNotEmpty()) {
                Text("RECENT INPUT", color = Color(0xFF9AA4B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                history.takeLast(5).asReversed().forEach { previous ->
                    TextButton(onClick = { command = previous }) {
                        Text(previous.take(72), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
            Text(status, color = Color(0xFF67DCE8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Box(
                Modifier.fillMaxWidth()
                    .height(360.dp)
                    .background(Color(0xFF05070C), RoundedCornerShape(16.dp))
                    .onSizeChanged { size ->
                        val widthDp = with(density) { size.width.toDp().value }
                        val columns = (widthDp / 7.2f).toInt().coerceIn(20, 180)
                        session?.resize(rows = 21, columns = columns)
                    }
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
                "The shell now receives a real PTY with terminal echo, line discipline, window sizing and process-group Ctrl+C. Full ANSI/VT screen rendering (for example vim, htop and ncurses apps) is a separate terminal-renderer milestone.",
                color = Color(0xFF9AA4B8),
                fontSize = 10.sp
            )
        }
    }
}
