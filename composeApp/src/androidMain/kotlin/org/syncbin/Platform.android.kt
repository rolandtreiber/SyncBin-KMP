package org.syncbin

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.ktor.serialization.kotlinx.json.json

actual class PlatformContext(
    val context: Context,
)

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

@Composable
actual fun rememberPlatformContext(): PlatformContext {
    val context = LocalContext.current.applicationContext
    return remember(context) { PlatformContext(context) }
}

private class AndroidSessionStore(
    context: Context,
) : SessionStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("syncbin_preferences", Context.MODE_PRIVATE)

    override fun loadCurrentSessionId(): String? {
        return preferences.getString("current_session_id", null)
    }

    override fun saveCurrentSessionId(sessionId: String) {
        preferences.edit().putString("current_session_id", sessionId).apply()
    }

    override fun loadQuickAccess(): List<String> {
        return preferences.getStringSet("quick_access", emptySet()).orEmpty().toList().sorted()
    }

    override fun saveQuickAccess(sessionIds: List<String>) {
        preferences.edit().putStringSet("quick_access", sessionIds.toSet()).apply()
    }
}

actual fun createSessionStore(platformContext: PlatformContext): SessionStore {
    return AndroidSessionStore(platformContext.context)
}

actual fun createHttpClient() = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
        json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
    }
}

@Composable
actual fun rememberPlatformBridge(controller: SyncBinController): PlatformBridge {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            readPickedFile(context, uri)?.let(controller::uploadPickedFile)
        }
    }

    val scanner: GmsBarcodeScanner? = remember(activity) {
        activity?.let {
            val options = GmsBarcodeScannerOptions.Builder()
                .allowManualInput()
                .enableAutoZoom()
                .build()
            GmsBarcodeScanning.getClient(it, options)
        }
    }

    return remember(context, activity, filePicker, scanner, controller) {
        object : PlatformBridge {
            override fun pickFile() {
                filePicker.launch(arrayOf("*/*"))
            }

            override fun copyText(text: String) {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SyncBin session", text))
            }

            override fun scanQrCode() {
                val barcodeScanner = scanner ?: return
                barcodeScanner.startScan()
                    .addOnSuccessListener { barcode ->
                        barcode.rawValue?.let(controller::handleScannedSession)
                    }
                    .addOnFailureListener {
                        controller.consumeMessage()
                    }
            }

            override fun downloadFile(fileName: String, url: String) {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(fileName)
                    .setDescription("Downloading $fileName")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    .setAllowedOverMetered(true)
                val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (mimeType != null) {
                    request.setMimeType(mimeType)
                }
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
            }
        }
    }
}

@Composable
actual fun PreviewImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
actual fun QrCodeImage(
    value: String,
    modifier: Modifier,
) {
    val bitmap = remember(value) { generateQrBitmap(value) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR code",
        modifier = modifier,
    )
}

private fun readPickedFile(context: Context, uri: Uri): PickedFile? {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        } ?: "upload"
    val mimeType = resolver.getType(uri)
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return PickedFile(name = name, bytes = bytes, mimeType = mimeType)
}

private fun generateQrBitmap(value: String): android.graphics.Bitmap {
    val matrix = QRCodeWriter().encode(
        value,
        BarcodeFormat.QR_CODE,
        512,
        512,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val bitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until 512) {
        for (y in 0 until 512) {
            bitmap.setPixel(
                x,
                y,
                if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
            )
        }
    }
    return bitmap
}
