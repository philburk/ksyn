package com.softsynth.ksyn

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

@Composable
actual fun getPlatformContext(): Any? = null

actual suspend fun pickAudioFileBytes(context: Any?): Pair<String, ByteArray>? =
    withContext(Dispatchers.IO) {
        val dialog = FileDialog(null as Frame?, "Select WAV or AIFF Audio File", FileDialog.LOAD)
        dialog.filenameFilter = FilenameFilter { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".wav") || lower.endsWith(".aif") || lower.endsWith(".aiff")
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val fileName = dialog.file
        if (dir != null && fileName != null) {
            val file = File(dir, fileName)
            if (file.exists() && file.isFile) {
                Pair(file.name, file.readBytes())
            } else {
                null
            }
        } else {
            null
        }
    }