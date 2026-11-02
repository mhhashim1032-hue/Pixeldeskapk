package com.pixeldesk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LinuxTerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val terminal = remember { TerminalEmulator(rows = 24, columns = 80) }
    val workspace = remember {
        val available = WorkspaceStorage.list(context).filter { candidate ->
            runCatching { RootfsValidator.validate(candidate.rootFilesystem) }.isSuccess
        }
        val selectedId = EnvironmentRuntimeManager.selectedEnvironmentId()
        available.firstOrNull { it.id == selectedId } ?: available.firstOrNull()
    }
    var session by remember { mutableStateOf<ProotPtySession?>(null) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var terminalRows by remember { mutableStateOf(terminal.snapshot) }
    var status by remember { mutableStateOf("STARTING") }
    var notice by remember { mutableStateOf("PIXEL DESK · Debian ARM64 · VT terminal") }

    LaunchedEffect(workspace) {
        if (workspace == null) {
            status = "NOT INSTALLED"
            notice = "Install Debian 12 ARM64 from Environments before opening the terminal."
        } else {
            try {
                session = EnvironmentRuntimeManager.startPty(context, workspace)
                status = "CONNECTED · PTY"
            } catch (error: Exception) {
                status = "CONNECTION ERROR"
                notice = error.message ?: "Could not start the Debian PTY shell."
            }
        }
    }

    LaunchedEffect(session) {
        val active = session ?: return@LaunchedEffect
        try {
            while (active.isAlive) {
                val bytes = active.readOutput() ?: break
                terminal.feed(bytes)
                terminalRows = terminal.snapshot
                notice = "Connected · interactive PTY · ANSI/VT basic mode"
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            notice = error.message ?: "Terminal output stopped."
        } finally {
            if (session === active) {
                status = "DISCONNECTED"
                session = null
            }
        }
    }

    fun send(value: String) {
        val active = session ?: return
        runCatching { active.writeText(value) }
            .onFailure { notice = "Input error: ${it.message}" }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090B12)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text("Linux Terminal", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Native POSIX PTY · ANSI/VT renderer", color = Color(0xFF9AA4B8), fontSize = 12.sp)
            Text(
                "${workspace?.displayName ?: "No verified Debian environment"} · $status",
                color = if (session != null) Color(0xFF67DCE8) else Color(0xFF9AA4B8),
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Send terminal input") },
                placeholder = { Text("Type text or an interactive command") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        send(inputText)
                        inputText = ""
                    },
                    enabled = session != null && inputText.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Send") }
                TextButton(onClick = { send("\r") }, enabled = session != null) { Text("Enter") }
                TextButton(onClick = { send("\t") }, enabled = session != null) { Text("Tab") }
                TextButton(onClick = { send("\u001b") }, enabled = session != null) { Text("Esc") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { send("\u001b[A") }, enabled = session != null) { Text("↑") }
                TextButton(onClick = { send("\u001b[B") }, enabled = session != null) { Text("↓") }
                TextButton(onClick = { send("\u001b[D") }, enabled = session != null) { Text("←") }
                TextButton(onClick = { send("\u001b[C") }, enabled = session != null) { Text("→") }
                TextButton(
                    onClick = {
                        val sent = session?.interrupt() == true
                        if (!sent) notice = "Could not send Ctrl+C to the PTY."
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
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        terminal.clear()
                        terminalRows = terminal.snapshot
                    }
                ) { Text("Clear screen") }
                TextButton(
                    onClick = {
                        if (workspace != null) {
                            scope.launch {
                                status = "CONNECTING"
                                try {
                                    session = EnvironmentRuntimeManager.startPty(context, workspace)
                                    status = "CONNECTED · PTY"
                                } catch (error: Exception) {
                                    status = "CONNECTION ERROR"
                                    notice = error.message ?: "Could not reconnect."
                                }
                            }
                        }
                    },
                    enabled = session == null && workspace != null
                ) { Text("Reconnect") }
            }
            Box(
                Modifier.fillMaxWidth()
                    .height(390.dp)
                    .background(Color(0xFF05070C), RoundedCornerShape(14.dp))
                    .onSizeChanged { size ->
                        val widthDp = with(density) { size.width.toDp().value }
                        val columns = (widthDp / 7.2f).toInt().coerceIn(20, 180)
                        terminal.resize(rows = 24, columns = columns)
                        session?.resize(rows = 24, columns = columns)
                        terminalRows = terminal.snapshot
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    terminalRows.forEachIndexed { rowIndex, row ->
                        Text(
                            buildAnnotatedString {
                                row.forEachIndexed { columnIndex, cell ->
                                    val isCursor = terminal.cursorVisible &&
                                        rowIndex == terminal.cursorRow &&
                                        columnIndex == terminal.cursorColumn.coerceIn(0, row.lastIndex)
                                    val foreground = if (isCursor) cell.background else cell.foreground
                                    val background = if (isCursor) cell.foreground else cell.background
                                    withStyle(
                                        SpanStyle(
                                            color = Color(foreground),
                                            background = Color(background),
                                            fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal
                                        )
                                    ) {
                                        append(cell.character)
                                    }
                                }
                            },
                            color = Color(0xFFD6E3E8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            softWrap = false
                        )
                    }
                }
            }
            Text(notice, color = Color(0xFF9AA4B8), fontSize = 10.sp)
            Text(
                "Supports common cursor movement, erase/scroll commands, ANSI 16/256/true-color SGR, alternate screen and UTF-8 input. Advanced xterm extensions and some full-screen apps may still require compatibility work.",
                color = Color(0xFF778398),
                fontSize = 10.sp
            )
        }
    }
}
