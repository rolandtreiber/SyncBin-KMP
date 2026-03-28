package org.syncbin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import syncbin.composeapp.generated.resources.Res
import syncbin.composeapp.generated.resources.syncbin_logo

private val AccentColor = Color(0xFFA5F500)
private val LightBackground = Color(0xFFF7F7F2)
private val LightSurface = Color.White
private val LightHeader = Color(0xFF333333)
private val LightTitle = Color(0xFF333333)
private val DarkBackground = Color(0xFF141414)
private val DarkSurface = Color(0xFF202020)
private val DarkHeader = Color(0xFF0F0F0F)
private val DarkTitle = Color(0xFFE4E4E4)

private data class SyncBinColors(
    val headerBackground: Color,
    val screenBackground: Color,
    val surfaceBackground: Color,
    val titleColor: Color,
    val textFieldBackground: Color,
    val textFieldBorder: Color,
    val textPlaceholder: Color,
    val pillBackground: Color,
    val pillText: Color,
    val chipBackground: Color,
    val emptyText: Color,
    val actionButtonBackground: Color,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun App() {
    val darkTheme = isSystemInDarkTheme()
    val colors = remember(darkTheme) {
        if (darkTheme) {
            SyncBinColors(
                headerBackground = DarkHeader,
                screenBackground = DarkBackground,
                surfaceBackground = DarkSurface,
                titleColor = DarkTitle,
                textFieldBackground = Color(0xFF1A1A1A),
                textFieldBorder = Color.White.copy(alpha = 0.12f),
                textPlaceholder = DarkTitle.copy(alpha = 0.45f),
                pillBackground = Color.White.copy(alpha = 0.08f),
                pillText = DarkTitle.copy(alpha = 0.82f),
                chipBackground = Color.White.copy(alpha = 0.08f),
                emptyText = DarkTitle.copy(alpha = 0.55f),
                actionButtonBackground = Color(0xFF4A4A4A),
            )
        } else {
            SyncBinColors(
                headerBackground = LightHeader,
                screenBackground = LightBackground,
                surfaceBackground = LightSurface,
                titleColor = LightTitle,
                textFieldBackground = Color.White,
                textFieldBorder = Color.Black.copy(alpha = 0.18f),
                textPlaceholder = LightTitle.copy(alpha = 0.35f),
                pillBackground = Color.Black.copy(alpha = 0.08f),
                pillText = Color.Black.copy(alpha = 0.65f),
                chipBackground = Color.Black.copy(alpha = 0.08f),
                emptyText = LightTitle.copy(alpha = 0.55f),
                actionButtonBackground = LightHeader,
            )
        }
    }
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = AccentColor,
            secondary = AccentColor,
            background = colors.screenBackground,
            surface = colors.surfaceBackground,
            onPrimary = Color.Black,
            onBackground = colors.titleColor,
            onSurface = colors.titleColor,
        )
    } else {
        lightColorScheme(
            primary = AccentColor,
            secondary = AccentColor,
            background = colors.screenBackground,
            surface = colors.surfaceBackground,
            onPrimary = Color.Black,
            onBackground = colors.titleColor,
            onSurface = colors.titleColor,
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        val platformContext = rememberPlatformContext()
        val controller = rememberSyncBinController(platformContext)
        val bridge = rememberPlatformBridge(controller)
        val state by controller.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(state.message) {
            val message = state.message ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(message)
            controller.consumeMessage()
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = colors.screenBackground,
        ) { innerPadding ->
            val safeInsets = WindowInsets.statusBars.union(WindowInsets.navigationBars)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.screenBackground)
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        safeInsets.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            ) {
                SessionHeader(
                    colors = colors,
                    state = state,
                    onSessionIdChange = controller::onSessionIdChanged,
                    onSaveSession = controller::addCurrentSessionToQuickAccess,
                    onCopySession = {
                        bridge.copyText("${FirebaseConfig.shareBaseUrl}${state.sessionId}")
                        controller.consumeMessage()
                    },
                    onShowQr = controller::showQrSheet,
                    onScanQr = bridge::scanQrCode,
                )
                QuickAccessRow(
                    colors = colors,
                    sessions = state.quickAccess,
                    currentSessionId = state.sessionId,
                    onSessionSelected = controller::onQuickAccessSelected,
                    onRemoveSession = controller::removeQuickAccess,
                )
                if (state.sessionVisible) {
                    EditorContent(
                        colors = colors,
                        state = state,
                        onTextChanged = controller::onTextChanged,
                        onPickFile = bridge::pickFile,
                        onDeleteFile = controller::deleteFile,
                        onOpenPreview = controller::openPreview,
                        onDownloadFile = { fileName ->
                            bridge.downloadFile(
                                fileName = fileName,
                                url = SessionRepository().publicFileUrl(state.sessionId, fileName),
                            )
                        },
                    )
                } else {
                    EmptySessionState(colors)
                }
            }
        }

        val previewUrl = state.previewUrl
        if (state.previewSheetVisible && previewUrl != null) {
            ModalBottomSheet(
                onDismissRequest = controller::dismissPreview,
                containerColor = colors.surfaceBackground,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.previewFileName.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.titleColor,
                    )
                    Spacer(Modifier.height(16.dp))
                    PreviewImage(
                        url = previewUrl,
                        contentDescription = state.previewFileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (state.qrSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = controller::dismissQrSheet,
                containerColor = colors.surfaceBackground,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = AccentColor,
                        tonalElevation = 2.dp,
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            QrCodeImage(
                                value = "${FirebaseConfig.shareBaseUrl}${state.sessionId}",
                                modifier = Modifier.size(220.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.sessionId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.titleColor,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(
    colors: SyncBinColors,
    state: SyncBinUiState,
    onSessionIdChange: (String) -> Unit,
    onSaveSession: () -> Unit,
    onCopySession: () -> Unit,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.headerBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(Res.drawable.syncbin_logo),
                contentDescription = "SyncBin logo",
                modifier = Modifier.width(160.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionInput(
                colors = colors,
                value = state.sessionId,
                onValueChange = onSessionIdChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            HeaderAction("+", "Save", onSaveSession)
            HeaderAction("⧉", "Copy", onCopySession)
            HeaderAction("▦", "Show QR", onShowQr)
            HeaderAction("⌁", "Scan QR", onScanQr)
        }
    }
}

@Composable
private fun SessionInput(
    colors: SyncBinColors,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.titleColor),
        singleLine = true,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .background(colors.textFieldBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.isBlank()) {
                    Text(
                        text = "Session Id...",
                        color = colors.textPlaceholder,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun HeaderAction(
    symbol: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Text(
            text = symbol,
            color = AccentColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun QuickAccessRow(
    colors: SyncBinColors,
    sessions: List<String>,
    currentSessionId: String,
    onSessionSelected: (String) -> Unit,
    onRemoveSession: (String) -> Unit,
) {
    if (sessions.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        sessions.forEach { session ->
            Surface(
                modifier = Modifier.padding(horizontal = 4.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (session == currentSessionId) colors.pillBackground else Color.Transparent,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = session,
                        modifier = Modifier.clickable { onSessionSelected(session) },
                        color = colors.pillText,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "✕",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onRemoveSession(session) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorContent(
    colors: SyncBinColors,
    state: SyncBinUiState,
    onTextChanged: (String) -> Unit,
    onPickFile: () -> Unit,
    onDeleteFile: (String) -> Unit,
    onOpenPreview: (String) -> Unit,
    onDownloadFile: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(
            text = "Text",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.titleColor,
        )
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = state.text,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .border(1.dp, colors.textFieldBorder, RoundedCornerShape(10.dp))
                .background(colors.textFieldBackground, RoundedCornerShape(10.dp))
                .padding(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.titleColor),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.text.isEmpty()) {
                        Text(
                            text = "Type or paste text here...",
                            color = colors.textPlaceholder,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Files",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.titleColor,
            )
            Button(
                onClick = onPickFile,
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.actionButtonBackground,
                    contentColor = AccentColor,
                    disabledContainerColor = colors.actionButtonBackground.copy(alpha = 0.7f),
                    disabledContentColor = AccentColor.copy(alpha = 0.7f),
                ),
            ) {
                Text("Select a file")
            }
        }
        Spacer(Modifier.height(10.dp))
        if (state.busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Working...", color = colors.titleColor)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (state.files.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceBackground,
            ) {
                Text(
                    text = "No files uploaded for this session yet.",
                    modifier = Modifier.padding(16.dp),
                    color = colors.emptyText,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.files.forEach { fileName ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceBackground,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.titleColor,
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (isImageFile(fileName)) {
                                    QuickActionChip(
                                        label = "Preview",
                                        colors = colors,
                                        labelColor = colors.titleColor,
                                    ) { onOpenPreview(fileName) }
                                }
                                QuickActionChip(
                                    label = "Download",
                                    colors = colors,
                                    labelColor = colors.titleColor,
                                ) { onDownloadFile(fileName) }
                                QuickActionChip(
                                    label = "Delete",
                                    colors = colors,
                                    containerColor = Color.Red.copy(alpha = 0.14f),
                                    labelColor = Color.Red,
                                ) { onDeleteFile(fileName) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    colors: SyncBinColors,
    containerColor: Color = colors.chipBackground,
    labelColor: Color = Color.Black,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, color = labelColor) },
        colors = AssistChipDefaults.assistChipColors(containerColor = containerColor),
    )
}

@Composable
private fun EmptySessionState(colors: SyncBinColors) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Session unavailable",
            style = MaterialTheme.typography.titleMedium,
            color = colors.emptyText,
        )
    }
}

private fun isImageFile(fileName: String): Boolean {
    val lower = fileName.lowercase()
    return listOf(
        ".jpg",
        ".jpeg",
        ".png",
        ".gif",
        ".svg",
        ".bmp",
        ".webp",
        ".tif",
        ".tiff",
        ".heic",
        ".heif",
        ".avif",
        ".ico",
        ".pcx",
    ).any(lower::endsWith)
}
