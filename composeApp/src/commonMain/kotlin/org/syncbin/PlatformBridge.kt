package org.syncbin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class PickedFile(
    val name: String,
    val bytes: ByteArray,
    val mimeType: String?,
)

interface PlatformBridge {
    fun pickFile()
    fun copyText(text: String)
    fun scanQrCode()
    fun downloadFile(fileName: String, url: String)
}

@Composable
expect fun rememberPlatformBridge(controller: SyncBinController): PlatformBridge

@Composable
expect fun PreviewImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
)

@Composable
expect fun QrCodeImage(
    value: String,
    modifier: Modifier = Modifier,
)
