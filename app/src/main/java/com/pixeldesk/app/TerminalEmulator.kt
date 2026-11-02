package com.pixeldesk.app

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

internal data class TerminalCell(
    val character: Char = ' ',
    val foreground: Int = DEFAULT_FOREGROUND,
    val background: Int = DEFAULT_BACKGROUND,
    val bold: Boolean = false
) {
    companion object {
        val DEFAULT_FOREGROUND: Int = 0xFFD6E3E8.toInt()
        val DEFAULT_BACKGROUND: Int = 0xFF05070C.toInt()
    }
}

/**
 * Small, bounded VT-style terminal screen model for the interactive PRoot PTY.
 * Supports common CSI cursor/erase/scroll commands and ANSI SGR colors. It is
 * deliberately not advertised as a complete xterm implementation.
 */
internal class TerminalEmulator(
    rows: Int = 24,
    columns: Int = 80
) {
    var rows: Int = rows.coerceIn(2, 300)
        private set
    var columns: Int = columns.coerceIn(2, 500)
        private set

    private var cells = blankGrid(this.rows, this.columns)
    private var primaryCells: Array<Array<TerminalCell>>? = null
    private var cursorX = 0
    private var cursorY = 0
    private var savedX = 0
    private var savedY = 0
    private var scrollTop = 0
    private var scrollBottom = this.rows - 1
    private var foreground = TerminalCell.DEFAULT_FOREGROUND
    private var background = TerminalCell.DEFAULT_BACKGROUND
    private var bold = false
    private var parserState = GROUND
    private val csi = StringBuilder()
    private var utf8Carry = ByteArray(0)

    var cursorVisible: Boolean = true
        private set

    val cursorRow: Int get() = cursorY
    val cursorColumn: Int get() = cursorX

    val snapshot: List<List<TerminalCell>>
        get() = cells.map { row -> row.toList() }

    fun feed(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val combined = ByteArray(utf8Carry.size + bytes.size)
        utf8Carry.copyInto(combined)
        bytes.copyInto(combined, utf8Carry.size)
        val input = ByteBuffer.wrap(combined)
        val decoded = CharBuffer.allocate(combined.size + 2)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        decoder.decode(input, decoded, false)
        utf8Carry = ByteArray(input.remaining())
        input.get(utf8Carry)
        decoded.flip()
        while (decoded.hasRemaining()) process(decoded.get())
    }

    fun feed(text: String) = feed(text.toByteArray(Charsets.UTF_8))

    fun clear() {
        eraseDisplay(2)
        cursorX = 0
        cursorY = 0
    }

    fun reset() {
        cells = blankGrid(rows, columns)
        primaryCells = null
        cursorX = 0
        cursorY = 0
        savedX = 0
        savedY = 0
        scrollTop = 0
        scrollBottom = rows - 1
        foreground = TerminalCell.DEFAULT_FOREGROUND
        background = TerminalCell.DEFAULT_BACKGROUND
        bold = false
        cursorVisible = true
        parserState = GROUND
        csi.setLength(0)
        utf8Carry = ByteArray(0)
    }

    fun resize(newRows: Int, newColumns: Int) {
        val targetRows = newRows.coerceIn(2, 300)
        val targetColumns = newColumns.coerceIn(2, 500)
        if (targetRows == rows && targetColumns == columns) return
        val resized = blankGrid(targetRows, targetColumns)
        for (y in 0 until minOf(rows, targetRows)) {
            for (x in 0 until minOf(columns, targetColumns)) resized[y][x] = cells[y][x]
        }
        cells = resized
        rows = targetRows
        columns = targetColumns
        cursorX = cursorX.coerceIn(0, columns - 1)
        cursorY = cursorY.coerceIn(0, rows - 1)
        savedX = savedX.coerceIn(0, columns - 1)
        savedY = savedY.coerceIn(0, rows - 1)
        scrollTop = 0
        scrollBottom = rows - 1
        primaryCells = null
    }

    private fun process(char: Char) {
        when (parserState) {
            GROUND -> when {
                char == ESC -> parserState = ESCAPE
                char == '\r' -> cursorX = 0
                char == '\n' -> lineFeed()
                char == '\b' -> cursorX = (cursorX - 1).coerceAtLeast(0)
                char == '\t' -> cursorX = (((cursorX / 8) + 1) * 8).coerceAtMost(columns - 1)
                char.code < 0x20 || char == '\u007f' -> Unit
                else -> put(char)
            }
            ESCAPE -> {
                parserState = GROUND
                when (char) {
                    '[' -> { csi.setLength(0); parserState = CSI }
                    ']' -> parserState = OSC
                    '7' -> { savedX = cursorX; savedY = cursorY }
                    '8' -> { cursorX = savedX.coerceIn(0, columns - 1); cursorY = savedY.coerceIn(0, rows - 1) }
                    'D' -> lineFeed()
                    'M' -> reverseIndex()
                    'E' -> { cursorX = 0; lineFeed() }
                    'c' -> reset()
                    else -> Unit
                }
            }
            CSI -> {
                if (char.code in 0x40..0x7e) {
                    executeCsi(csi.toString(), char)
                    csi.setLength(0)
                    parserState = GROUND
                } else if (csi.length < MAX_CSI_LENGTH) {
                    csi.append(char)
                } else {
                    csi.setLength(0)
                    parserState = GROUND
                }
            }
            OSC -> when (char) {
                '\u0007' -> parserState = GROUND
                ESC -> parserState = OSC_ESCAPE
                else -> Unit
            }
            OSC_ESCAPE -> parserState = if (char == '\\') GROUND else OSC
        }
    }

    private fun put(char: Char) {
        if (cursorX >= columns) {
            cursorX = 0
            lineFeed()
        }
        cells[cursorY][cursorX] = TerminalCell(char, foreground, background, bold)
        cursorX++
    }

    private fun lineFeed() {
        if (cursorY == scrollBottom) scrollUp(1)
        else cursorY = (cursorY + 1).coerceAtMost(rows - 1)
    }

    private fun reverseIndex() {
        if (cursorY == scrollTop) scrollDown(1)
        else cursorY = (cursorY - 1).coerceAtLeast(0)
    }

    private fun executeCsi(raw: String, command: Char) {
        val privateMode = raw.startsWith("?")
        val parameterText = raw.trimStart('?', '>')
        val params = if (parameterText.isEmpty()) emptyList() else
            parameterText.split(';').map { it.toIntOrNull() ?: 0 }
        fun p(index: Int, default: Int = 1): Int = params.getOrNull(index)?.takeIf { it != 0 } ?: default
        when (command) {
            'A' -> cursorY = (cursorY - p(0)).coerceAtLeast(0)
            'B' -> cursorY = (cursorY + p(0)).coerceAtMost(rows - 1)
            'C' -> cursorX = (cursorX + p(0)).coerceAtMost(columns - 1)
            'D' -> cursorX = (cursorX - p(0)).coerceAtLeast(0)
            'E' -> { cursorY = (cursorY + p(0)).coerceAtMost(rows - 1); cursorX = 0 }
            'F' -> { cursorY = (cursorY - p(0)).coerceAtLeast(0); cursorX = 0 }
            'G' -> cursorX = (p(0) - 1).coerceIn(0, columns - 1)
            'd' -> cursorY = (p(0) - 1).coerceIn(0, rows - 1)
            'H', 'f' -> {
                cursorY = (p(0) - 1).coerceIn(0, rows - 1)
                cursorX = (p(1) - 1).coerceIn(0, columns - 1)
            }
            'J' -> eraseDisplay(params.firstOrNull() ?: 0)
            'K' -> eraseLine(params.firstOrNull() ?: 0)
            'm' -> applySgr(params)
            's' -> { savedX = cursorX; savedY = cursorY }
            'u' -> { cursorX = savedX.coerceIn(0, columns - 1); cursorY = savedY.coerceIn(0, rows - 1) }
            'r' -> {
                scrollTop = (p(0) - 1).coerceIn(0, rows - 1)
                scrollBottom = ((params.getOrNull(1) ?: rows).let { if (it == 0) rows else it } - 1)
                    .coerceIn(scrollTop, rows - 1)
                cursorX = 0
                cursorY = 0
            }
            '@' -> insertCharacters(p(0))
            'P' -> deleteCharacters(p(0))
            'X' -> eraseCharacters(p(0))
            'L' -> insertLines(p(0))
            'M' -> deleteLines(p(0))
            'S' -> scrollUp(p(0))
            'T' -> scrollDown(p(0))
            'h', 'l' -> if (privateMode) {
                val enabled = command == 'h'
                if (params.contains(1049)) switchAlternateScreen(enabled)
                if (params.contains(25)) cursorVisible = enabled
            }
        }
    }

    private fun applySgr(params: List<Int>) {
        if (params.isEmpty()) {
            resetAttributes()
            return
        }
        var index = 0
        while (index < params.size) {
            when (val code = params[index]) {
                0 -> resetAttributes()
                1 -> bold = true
                22 -> bold = false
                in 30..37 -> foreground = ANSI_COLORS[code - 30]
                in 90..97 -> foreground = ANSI_COLORS[code - 90 + 8]
                39 -> foreground = TerminalCell.DEFAULT_FOREGROUND
                in 40..47 -> background = ANSI_COLORS[code - 40]
                in 100..107 -> background = ANSI_COLORS[code - 100 + 8]
                49 -> background = TerminalCell.DEFAULT_BACKGROUND
                38, 48 -> {
                    val isForeground = code == 38
                    when (params.getOrNull(index + 1)) {
                        5 -> {
                            val color = color256(params.getOrNull(index + 2) ?: 0)
                            if (isForeground) foreground = color else background = color
                            index += 2
                        }
                        2 -> {
                            val red = params.getOrNull(index + 2) ?: 0
                            val green = params.getOrNull(index + 3) ?: 0
                            val blue = params.getOrNull(index + 4) ?: 0
                            val color = argb(red, green, blue)
                            if (isForeground) foreground = color else background = color
                            index += 4
                        }
                    }
                }
            }
            index++
        }
    }

    private fun resetAttributes() {
        foreground = TerminalCell.DEFAULT_FOREGROUND
        background = TerminalCell.DEFAULT_BACKGROUND
        bold = false
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseRange(cursorY, cursorX, columns - 1)
                for (y in cursorY + 1 until rows) eraseRange(y, 0, columns - 1)
            }
            1 -> {
                for (y in 0 until cursorY) eraseRange(y, 0, columns - 1)
                eraseRange(cursorY, 0, cursorX)
            }
            2, 3 -> for (y in 0 until rows) eraseRange(y, 0, columns - 1)
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> eraseRange(cursorY, cursorX, columns - 1)
            1 -> eraseRange(cursorY, 0, cursorX)
            2 -> eraseRange(cursorY, 0, columns - 1)
        }
    }

    private fun eraseRange(row: Int, start: Int, end: Int) {
        if (row !in 0 until rows || start > end) return
        for (x in start.coerceIn(0, columns - 1)..end.coerceIn(0, columns - 1)) {
            cells[row][x] = blankCell()
        }
    }

    private fun insertCharacters(count: Int) {
        if (cursorX !in 0 until columns) return
        val amount = count.coerceAtLeast(1).coerceAtMost(columns - cursorX)
        for (x in columns - 1 downTo cursorX + amount) cells[cursorY][x] = cells[cursorY][x - amount]
        for (x in cursorX until cursorX + amount) cells[cursorY][x] = blankCell()
    }

    private fun deleteCharacters(count: Int) {
        if (cursorX !in 0 until columns) return
        val amount = count.coerceAtLeast(1).coerceAtMost(columns - cursorX)
        for (x in cursorX until columns - amount) cells[cursorY][x] = cells[cursorY][x + amount]
        for (x in columns - amount until columns) cells[cursorY][x] = blankCell()
    }

    private fun eraseCharacters(count: Int) {
        if (cursorX !in 0 until columns) return
        val end = (cursorX + count.coerceAtLeast(1) - 1).coerceAtMost(columns - 1)
        eraseRange(cursorY, cursorX, end)
    }

    private fun insertLines(count: Int) {
        if (cursorY !in scrollTop..scrollBottom) return
        val amount = count.coerceIn(1, scrollBottom - cursorY + 1)
        for (y in scrollBottom downTo cursorY + amount) cells[y] = cells[y - amount].copyOf()
        for (y in cursorY until cursorY + amount) cells[y] = blankRow(columns)
    }

    private fun deleteLines(count: Int) {
        if (cursorY !in scrollTop..scrollBottom) return
        val amount = count.coerceIn(1, scrollBottom - cursorY + 1)
        for (y in cursorY..scrollBottom - amount) cells[y] = cells[y + amount].copyOf()
        for (y in scrollBottom - amount + 1..scrollBottom) cells[y] = blankRow(columns)
    }

    private fun scrollUp(count: Int) {
        val amount = count.coerceIn(1, scrollBottom - scrollTop + 1)
        repeat(amount) {
            for (y in scrollTop until scrollBottom) cells[y] = cells[y + 1]
            cells[scrollBottom] = blankRow(columns)
        }
    }

    private fun scrollDown(count: Int) {
        val amount = count.coerceIn(1, scrollBottom - scrollTop + 1)
        repeat(amount) {
            for (y in scrollBottom downTo scrollTop + 1) cells[y] = cells[y - 1]
            cells[scrollTop] = blankRow(columns)
        }
    }

    private fun switchAlternateScreen(enabled: Boolean) {
        if (enabled && primaryCells == null) {
            savedX = cursorX
            savedY = cursorY
            primaryCells = cells
            cells = blankGrid(rows, columns)
            cursorX = 0
            cursorY = 0
        } else if (!enabled && primaryCells != null) {
            cells = primaryCells!!
            primaryCells = null
            cursorX = savedX.coerceIn(0, columns - 1)
            cursorY = savedY.coerceIn(0, rows - 1)
        }
    }

    private fun blankCell() = TerminalCell(
        foreground = foreground,
        background = background,
        bold = bold
    )

    private fun blankRow(width: Int) = Array(width) { blankCell() }

    private fun blankGrid(height: Int, width: Int) = Array(height) { blankRow(width) }

    private fun color256(index: Int): Int {
        val value = index.coerceIn(0, 255)
        if (value < 16) return ANSI_COLORS[value]
        if (value >= 232) {
            val gray = 8 + (value - 232) * 10
            return argb(gray, gray, gray)
        }
        val cube = value - 16
        val red = cube / 36
        val green = (cube % 36) / 6
        val blue = cube % 6
        fun channel(component: Int) = if (component == 0) 0 else 55 + component * 40
        return argb(channel(red), channel(green), channel(blue))
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)

    companion object {
        private const val MAX_CSI_LENGTH = 64
        private const val GROUND = 0
        private const val ESCAPE = 1
        private const val CSI = 2
        private const val OSC = 3
        private const val OSC_ESCAPE = 4
        private const val ESC = '\u001b'

        private val ANSI_COLORS = intArrayOf(
            0xFF000000.toInt(), 0xFFCD3131.toInt(), 0xFF0DBC79.toInt(), 0xFFE5E510.toInt(),
            0xFF2472C8.toInt(), 0xFFBC3FBC.toInt(), 0xFF11A8CD.toInt(), 0xFFE5E5E5.toInt(),
            0xFF666666.toInt(), 0xFFF14C4C.toInt(), 0xFF23D18B.toInt(), 0xFFF5F543.toInt(),
            0xFF3B8EEA.toInt(), 0xFFD670D6.toInt(), 0xFF29B8DB.toInt(), 0xFFFFFFFF.toInt()
        )

        private fun blankGrid(rows: Int, columns: Int) =
            Array(rows) { Array(columns) { TerminalCell() } }
    }
}
