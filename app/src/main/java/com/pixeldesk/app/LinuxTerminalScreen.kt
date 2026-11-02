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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    var command by rememberSaveable { mutableStateOf("uname -a") }
    var output by remember { mutableStateOf("PIXEL DESK terminal\\nInstall Debian ARM64 to begin.") }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("NOT CONNECTED") }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090B12)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Linux Terminal", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Execute commands inside your local Debian ARM64 userspace.", color = Color(0xFF9AA4B8), fontSize = 13.sp)
            Text(
                workspace?.displayName ?: "No verified Debian environment",
                color = if (workspace != null) Color(0xFF67DCE8) else Color(0xFF9AA4B8),
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("Linux command") },
                placeholder = { Text("uname -m") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val selected = workspace ?: return@Button
                        scope.launch {
                            running = true
                            status = "RUNNING"
                            output = "$ " + command.trim() + "\\n"
                            try {
                                val result = ProotCommandRunner.run(context, selected, command.trim())
                                output += result.stdout
                                if (result.stderr.isNotBlank()) output += "\\n[stderr]\\n" + result.stderr
                                status = if (result.timedOut) "TIMED OUT"
                                    else if (result.success) "EXIT 0"
                                    else "EXIT " + (result.exitCode?.toString() ?: "ERROR")
                                output += "\\n\\n" + status + " · " + result.durationMs + " ms"
                            } catch (error: Exception) {
                                status = "ERROR"
                                output += "\\n" + (error.message ?: "Command failed.")
                            } finally {
                                running = false
                            }
                        }
                    },
                    enabled = workspace != null && !running && command.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (running) "Running…" else "Run command")
                }
                TextButton(onClick = {
                    output = "PIXEL DESK terminal\\nOutput cleared."
                    status = if (workspace != null) "READY" else "NOT CONNECTED"
                }) {
                    Text("Clear")
                }
            }
            Text(status, color = Color(0xFF67DCE8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Column(
                Modifier.fillMaxWidth()
                    .height(280.dp)
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
                "PRoot provides a Linux userspace, not a separate Linux kernel. Commands run locally in this app's sandbox.",
                color = Color(0xFF9AA4B8),
                fontSize = 10.sp
            )
        }
    }
}
