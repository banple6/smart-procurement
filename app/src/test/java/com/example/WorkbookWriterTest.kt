package com.smartprocurement.internal

import com.smartprocurement.internal.ui.writeWorkbookBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class WorkbookWriterTest {
    @Test
    fun `writer rejects empty server response before opening destination`() {
        var opened = false

        assertThrows(IllegalArgumentException::class.java) {
            writeWorkbookBytes(byteArrayOf()) {
                opened = true
                ByteArrayOutputStream()
            }
        }
        assertEquals(false, opened)
    }

    @Test
    fun `writer writes every byte and reports exact size`() {
        val destination = ByteArrayOutputStream()
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        val written = writeWorkbookBytes(bytes) { destination }

        assertEquals(bytes.size, written)
        assertArrayEquals(bytes, destination.toByteArray())
    }

    @Test
    fun `writer rejects unavailable output stream`() {
        assertThrows(IllegalStateException::class.java) {
            writeWorkbookBytes(byteArrayOf(1)) { null }
        }
    }
}
