package org.syncbin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

actual class PlatformContext

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

@Composable
actual fun rememberPlatformContext(): PlatformContext = remember { PlatformContext() }

private class IOSSessionStore : SessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun loadCurrentSessionId(): String? = defaults.stringForKey("current_session_id")

    override fun saveCurrentSessionId(sessionId: String) {
        defaults.setObject(sessionId, "current_session_id")
    }

    override fun loadQuickAccess(): List<String> {
        val value = defaults.stringForKey("quick_access") ?: return emptyList()
        return value.split("|").filter { it.isNotBlank() }
    }

    override fun saveQuickAccess(sessionIds: List<String>) {
        defaults.setObject(sessionIds.joinToString("|"), "quick_access")
    }
}

actual fun createSessionStore(platformContext: PlatformContext): SessionStore = IOSSessionStore()

actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

private object IOSBridgeState {
    var documentPickerDelegate: NSObject? = null
    var scannerDelegate: NSObject? = null
}

@Composable
actual fun rememberPlatformBridge(controller: SyncBinController): PlatformBridge {
    val scope = rememberCoroutineScope()
    val client = remember { createHttpClient() }

    return remember(controller, scope, client) {
        object : PlatformBridge {
            override fun pickFile() {
                presentDocumentPicker(controller)
            }

            override fun copyText(text: String) {
                platform.UIKit.UIPasteboard.generalPasteboard.string = text
            }

            override fun scanQrCode() {
                presentQrScanner(controller)
            }

            override fun downloadFile(fileName: String, url: String) {
                scope.launch {
                    runCatching {
                        val bytes = client.get(url).body<ByteArray>()
                        val tempFileUrl = writeTempFile(fileName, bytes)
                        presentShareSheet(tempFileUrl)
                    }
                }
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
    var image by remember(url) { mutableStateOf<UIImage?>(null) }
    val client = remember { createHttpClient() }

    LaunchedEffect(url) {
        image = runCatching {
            val bytes = client.get(url).body<ByteArray>()
            UIImage.imageWithData(bytes.toNSData())
        }.getOrNull()
    }

    UIKitView(
        factory = {
            UIImageView().apply {
                clipsToBounds = true
                contentMode = platform.UIKit.UIViewContentMode.UIViewContentModeScaleAspectFit
                backgroundColor = UIColor.whiteColor
            }
        },
        modifier = modifier,
        update = { view ->
            view.image = image
        },
    )
}

@Composable
actual fun QrCodeImage(
    value: String,
    modifier: Modifier,
) {
    PreviewImage(
        url = "https://api.qrserver.com/v1/create-qr-code/?size=440x440&data=${value.encodeURLPath()}",
        contentDescription = "QR code",
        modifier = modifier,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun presentDocumentPicker(controller: SyncBinController) {
    val picker = UIDocumentPickerViewController(
        documentTypes = listOf("public.item"),
        inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
    )
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            pickerController: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
            val path = url.path ?: return
            val data = platform.Foundation.NSFileManager.defaultManager.contentsAtPath(path) ?: return
            val fileName = url.lastPathComponent ?: "upload"
            controller.handlePickedFile(
                PickedFile(
                    name = fileName,
                    bytes = data.toByteArray(),
                    mimeType = null,
                ),
            )
        }
    }
    IOSBridgeState.documentPickerDelegate = delegate
    picker.delegate = delegate
    topViewController()?.presentViewController(picker, animated = true, completion = null)
}

private fun SyncBinController.handlePickedFile(file: PickedFile) {
    uploadPickedFile(file)
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun presentQrScanner(controller: SyncBinController) {
    val viewController = QRScannerViewController(
        onResult = { value ->
            controller.handleScannedSession(value)
        },
    )
    topViewController()?.presentViewController(viewController, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private class QRScannerViewController(
    private val onResult: (String) -> Unit,
) : UIViewController(nibName = null, bundle = null) {
    private val captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor
        setupScanner()
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        captureSession.startRunning()
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        captureSession.stopRunning()
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun setupScanner() {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) as? AVCaptureDeviceInput ?: return
        if (captureSession.canAddInput(input)) {
            captureSession.addInput(input)
        }

        val output = AVCaptureMetadataOutput()
        if (captureSession.canAddOutput(output)) {
            captureSession.addOutput(output)
        }

        val delegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
            override fun captureOutput(
                output: AVCaptureOutput,
                didOutputMetadataObjects: List<*>,
                fromConnection: AVCaptureConnection,
            ) {
                val value = (didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject)
                    ?.stringValue ?: return
                dismissViewControllerAnimated(true) {
                    onResult(value)
                }
            }
        }
        IOSBridgeState.scannerDelegate = delegate
        output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)

        val layer = AVCaptureVideoPreviewLayer(session = captureSession)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        layer.frame = view.bounds
        view.layer.addSublayer(layer)
        previewLayer = layer
    }
}

private fun topViewController(base: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController): UIViewController? {
    return when {
        base?.presentedViewController != null -> topViewController(base.presentedViewController)
        else -> base
    }
}

private fun dismissPresentedController() {
    topViewController()?.dismissViewControllerAnimated(true, completion = null)
}

private suspend fun writeTempFile(fileName: String, bytes: ByteArray): NSURL {
    val tempDirectory = NSTemporaryDirectory()
    val path = tempDirectory + fileName
    platform.Foundation.NSFileManager.defaultManager.createFileAtPath(
        path = path,
        contents = bytes.toNSData(),
        attributes = null,
    )
    return NSURL.fileURLWithPath(path)
}

private fun presentShareSheet(fileUrl: NSURL) {
    val controller = UIActivityViewController(activityItems = listOf(fileUrl), applicationActivities = null)
    topViewController()?.presentViewController(controller, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    return ByteArray(size).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}

private fun String.encodeURLPath(): String =
    replace("%", "%25")
        .replace(" ", "%20")
        .replace("/", "%2F")
        .replace(":", "%3A")
        .replace("?", "%3F")
        .replace("&", "%26")
        .replace("=", "%3D")
