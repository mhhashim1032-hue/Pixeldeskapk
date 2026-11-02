package com.pixeldesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {
    @Test
    fun writesCharactersAndHandlesCarriageReturnAndLineFeed() {
        val terminal = TerminalEmulator(3, 8)
        terminal.feed("hello\rY\r\nworld")
        assertEquals("Yello", terminal.snapshot[0].take(5).joinToString("") { it.character.toString() })
        assertEquals("world", terminal.snapshot[1].take(5).joinToString("") { it.character.toString() })
    }

    @Test
    fun positionsCursorAndOverwritesExistingCells() {
        val terminal = TerminalEmulator(3, 8)
        terminal.feed("abcdef\u001b[2;3HZ")
        assertEquals("abZdef", terminal.snapshot[1].take(6).joinToString("") { it.character.toString() })
    }

    @Test
    fun erasesLineAndScreenUsingCsiCommands() {
        val terminal = TerminalEmulator(3, 8)
        terminal.feed("first\r\nsecond\r\nthird\u001b[2;2H\u001b[K")
        assertEquals("s      ", terminal.snapshot[1].take(7).joinToString("") { it.character.toString() })
        terminal.feed("\u001b[2J")
        assertTrue(terminal.snapshot.flatten().all { it.character == ' ' })
    }

    @Test
    fun appliesAnsiForegroundBackgroundAndBoldAttributes() {
        val terminal = TerminalEmulator(2, 8)
        terminal.feed("\u001b[1;31;44mX\u001b[0mY")
        val red = terminal.snapshot[0][0]
        assertTrue(red.bold)
        assertEquals(0xFFCD3131.toInt(), red.foreground)
        assertEquals(0xFF2472C8.toInt(), red.background)
        assertFalse(terminal.snapshot[0][1].bold)
        assertEquals(TerminalCell.DEFAULT_FOREGROUND, terminal.snapshot[0][1].foreground)
    }

    @Test
    fun supports256AndTrueColorSgr() {
        val terminal = TerminalEmulator(2, 8)
        terminal.feed("\u001b[38;5;196mA\u001b[38;2;1;2;3mB")
        assertEquals(0xFFFF0000.toInt(), terminal.snapshot[0][0].foreground)
        assertEquals(0xFF010203.toInt(), terminal.snapshot[0][1].foreground)
    }

    @Test
    fun restoresPrimaryScreenAfterAlternateScreenUse() {
        val terminal = TerminalEmulator(2, 8)
        terminal.feed("primary")
        terminal.feed("\u001b[?1049halt screen\u001b[?1049l")
        assertEquals("primary ", terminal.snapshot[0].joinToString("") { it.character.toString() })
    }

    @Test
    fun preservesUtf8SequencesSplitAcrossPtyReads() {
        val terminal = TerminalEmulator(2, 8)
        val bytes = "é".toByteArray(Charsets.UTF_8)
        terminal.feed(bytes.copyOfRange(0, 1))
        terminal.feed(bytes.copyOfRange(1, bytes.size))
        assertEquals('é', terminal.snapshot[0][0].character)
    }

    @Test
    fun resizesGridWithoutLosingVisibleCells() {
        val terminal = TerminalEmulator(2, 4)
        terminal.feed("AB")
        terminal.resize(3, 6)
        assertEquals('A', terminal.snapshot[0][0].character)
        assertEquals('B', terminal.snapshot[0][1].character)
        assertEquals(3, terminal.rows)
        assertEquals(6, terminal.columns)
    }

    @Test
    fun supportsScrollingAndCursorVisibilityMode() {
        val terminal = TerminalEmulator(3, 6)
        terminal.feed("one\r\ntwo\r\nthree\r\nfour")
        assertEquals("two", terminal.snapshot[0].take(3).joinToString("") { it.character.toString() })
        terminal.feed("\u001b[?25l")
        assertFalse(terminal.cursorVisible)
        terminal.feed("\u001b[?25h")
        assertTrue(terminal.cursorVisible)
    }
    @Test
    fun parsesCsiSequencesSplitAcrossPtyChunks() {
        val terminal = TerminalEmulator(3, 8)
        terminal.feed("\u001b[")
        terminal.feed("2;3H")
        terminal.feed("X")
        assertEquals('X', terminal.snapshot[1][2].character)
    }

    @Test
    fun ignoresOscWindowTitleAndOtherMetadata() {
        val terminal = TerminalEmulator(2, 8)
        terminal.feed("\u001b]0;terminal title\u0007ready")
        assertEquals("ready", terminal.snapshot[0].take(5).joinToString("") { it.character.toString() })
    }

    @Test
    fun rightEdgeEraseAndInsertCommandsDoNotDeleteLastPrintedCell() {
        val terminal = TerminalEmulator(2, 4)
        terminal.feed("1234\u001b[X\u001b[@\u001b[P\u001b[K")
        assertEquals("1234", terminal.snapshot[0].take(4).joinToString("") { it.character.toString() })
    }

    @Test
    fun appliesScrollRegionWithoutMovingRowsOutsideIt() {
        val terminal = TerminalEmulator(4, 6)
        terminal.feed("top\r\nrow2\r\nrow3\r\nbottom")
        terminal.feed("\u001b[2;3r\u001b[3;1H\r\nnew")
        assertEquals("top", terminal.snapshot[0].take(3).joinToString("") { it.character.toString() })
        assertEquals("new", terminal.snapshot[2].take(3).joinToString("") { it.character.toString() })
        assertEquals("bottom", terminal.snapshot[3].take(6).joinToString("") { it.character.toString() })
    }

}
