package com.pixeldesk.app

internal object LinuxAppPreflight {
    private val safeExecutable = Regex("[A-Za-z0-9_+./-]{1,240}")

    fun executableToken(exec: String): String? {
        val token = firstExecToken(exec) ?: return null
        if (!safeExecutable.matches(token)) return null
        if (token.split('/').any { it == ".." }) return null
        if (token == "." || token == "./") return null
        return token
    }

    fun buildProbeCommand(apps: List<LinuxDesktopApp>): String {
        val tokens = apps.mapNotNull { executableToken(it.executable) }.distinct().take(MAX_COMMANDS)
        require(tokens.isNotEmpty()) { "No safe executable commands were found to check." }
        return tokens.joinToString("; ") { token ->
            "printf '%s\\t' '$token'; if command -v '$token' >/dev/null 2>&1; then printf 'AVAILABLE\\n'; else printf 'MISSING\\n'; fi"
        }
    }

    fun parseProbeResults(output: String): Map<String, Boolean> =
        output.lineSequence().mapNotNull { line ->
            val separator = line.indexOf('\t')
            if (separator <= 0) return@mapNotNull null
            val token = line.substring(0, separator)
            val status = line.substring(separator + 1).trim()
            if (!safeExecutable.matches(token) || status !in setOf("AVAILABLE", "MISSING")) {
                return@mapNotNull null
            }
            token to (status == "AVAILABLE")
        }.toMap()

    private fun firstExecToken(exec: String): String? {
        val token = StringBuilder()
        var quoted = false
        var escaped = false
        for (char in exec.trim()) {
            if (escaped) {
                token.append(char)
                escaped = false
            } else when {
                char == '\\' -> escaped = true
                char == '"' -> quoted = !quoted
                char.isWhitespace() && !quoted -> break
                else -> token.append(char)
            }
        }
        if (quoted || escaped) return null
        return token.toString().takeIf { it.isNotBlank() }
    }

    private const val MAX_COMMANDS = 100
}
