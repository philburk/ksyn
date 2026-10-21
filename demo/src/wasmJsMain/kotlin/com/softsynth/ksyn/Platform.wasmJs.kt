package com.softsynth.ksyn

import androidx.compose.runtime.Composable
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.await

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

@Composable
actual fun getPlatformContext(): Any? = null

@JsFun("""() => new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.wav,.aif,.aiff,audio/wav,audio/x-wav,audio/aiff,audio/x-aiff';
    input.style.display = 'none';
    document.body.appendChild(input);
    let settled = false;
    const finish = (name) => {
        if (!settled) {
            settled = true;
            if (input.parentNode) input.parentNode.removeChild(input);
            resolve(name);
        }
    };
    input.onchange = async () => {
        const file = input.files && input.files[0];
        if (!file) {
            finish('');
            return;
        }
        try {
            const arrayBuffer = await file.arrayBuffer();
            window._ksynPickedFileBytes = new Uint8Array(arrayBuffer);
            finish(file.name);
        } catch (e) {
            console.error('Error reading selected audio file:', e);
            finish('');
        }
    };
    input.oncancel = () => finish('');
    input.click();
})""")
private external fun pickWasmAudioFile(): Promise<JsString>

@JsFun("() => window._ksynPickedFileBytes ? window._ksynPickedFileBytes.length : 0")
private external fun getPickedFileLength(): Int

@JsFun("(index) => window._ksynPickedFileBytes[index]")
private external fun getPickedFileByte(index: Int): Byte

@JsFun("() => { window._ksynPickedFileBytes = null; }")
private external fun clearPickedFileBytes()

actual suspend fun pickAudioFileBytes(context: Any?): Pair<String, ByteArray>? {
    val fileName = pickWasmAudioFile().await<JsString>().toString()
    if (fileName.isEmpty()) return null
    val len = getPickedFileLength()
    if (len <= 0) {
        clearPickedFileBytes()
        return null
    }
    val bytes = ByteArray(len) { i -> getPickedFileByte(i) }
    clearPickedFileBytes()
    return Pair(fileName, bytes)
}
