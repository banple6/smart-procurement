package com.smartprocurement.internal.ui

import java.io.OutputStream

internal fun writeWorkbookBytes(bytes: ByteArray, openOutput: () -> OutputStream?): Int {
    require(bytes.isNotEmpty()) { "服务端返回的文件为空" }
    val output = openOutput() ?: error("无法写入所选文件")
    output.buffered().use { stream ->
        stream.write(bytes)
        stream.flush()
    }
    return bytes.size
}
