package org.syncbin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class SyncBinUiState(
    val sessionId: String = "",
    val quickAccess: List<String> = emptyList(),
    val sessionVisible: Boolean = false,
    val text: String = "",
    val files: List<String> = emptyList(),
    val previewUrl: String? = null,
    val previewFileName: String? = null,
    val previewSheetVisible: Boolean = false,
    val qrSheetVisible: Boolean = false,
    val infoSheetVisible: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class SyncBinController(
    platformContext: PlatformContext,
    private val repository: SessionRepository = SessionRepository(),
) {
    private val store = createSessionStore(platformContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(
        SyncBinUiState(
            sessionId = normalizeSessionId(store.loadCurrentSessionId().orEmpty()),
            quickAccess = store.loadQuickAccess(),
        ),
    )
    val state: StateFlow<SyncBinUiState> = _state.asStateFlow()

    private var sessionObservationJob: Job? = null
    private var sessionObservationActive = false
    private var textSyncJob: Job? = null
    private var pendingTextSessionId: String? = null
    private var pendingText: String? = null

    init {
        store.saveCurrentSessionId(_state.value.sessionId)
        updateSessionVisibility()
    }

    fun onAppForegrounded() {
        sessionObservationActive = true
        observeSession(_state.value.sessionId)
    }

    fun onAppBackgrounded() {
        sessionObservationActive = false
        flushPendingText()
        sessionObservationJob?.cancel()
        sessionObservationJob = null
    }

    fun onSessionIdChanged(rawValue: String) {
        val normalized = normalizeSessionId(rawValue)
        textSyncJob?.cancel()
        pendingTextSessionId = null
        pendingText = null
        _state.update {
            it.copy(
                sessionId = normalized,
                text = if (normalized != it.sessionId) "" else it.text,
                files = if (normalized != it.sessionId) emptyList() else it.files,
            )
        }
        store.saveCurrentSessionId(normalized)
        updateSessionVisibility()
        if (sessionObservationActive) {
            observeSession(normalized)
        }
    }

    fun onQuickAccessSelected(sessionId: String) {
        onSessionIdChanged(sessionId)
    }

    fun addCurrentSessionToQuickAccess() {
        val sessionId = state.value.sessionId
        val updated = (state.value.quickAccess + sessionId).distinct()
        _state.update { it.copy(quickAccess = updated, message = "Saved session $sessionId") }
        store.saveQuickAccess(updated)
    }

    fun removeQuickAccess(sessionId: String) {
        val updated = state.value.quickAccess.filterNot { it == sessionId }
        _state.update { it.copy(quickAccess = updated) }
        store.saveQuickAccess(updated)
    }

    fun onTextChanged(text: String) {
        _state.update { it.copy(text = text) }
        val sessionId = state.value.sessionId
        pendingTextSessionId = sessionId
        pendingText = text
        textSyncJob?.cancel()
        textSyncJob = scope.launch {
            delay(120)
            runCatching { repository.updateText(sessionId, text) }
                .onSuccess {
                    if (pendingTextSessionId == sessionId && pendingText == text) {
                        pendingTextSessionId = null
                        pendingText = null
                    }
                }
                .onFailure { showMessage("Failed to sync text") }
        }
    }

    fun uploadPickedFile(file: PickedFile) {
        val sessionId = state.value.sessionId
        scope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { repository.uploadFile(sessionId, file) }
                .onSuccess { showMessage("${file.name} uploaded") }
                .onFailure { showMessage("Failed to upload ${file.name}") }
            _state.update { it.copy(busy = false) }
        }
    }

    fun deleteFile(fileName: String) {
        val sessionId = state.value.sessionId
        scope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { repository.deleteFile(sessionId, fileName) }
                .onSuccess { showMessage("$fileName deleted") }
                .onFailure { showMessage("Failed to delete $fileName") }
            _state.update { it.copy(busy = false) }
        }
    }

    fun openPreview(fileName: String) {
        val sessionId = state.value.sessionId
        _state.update {
            it.copy(
                previewFileName = fileName,
                previewUrl = repository.publicFileUrl(sessionId, fileName),
                previewSheetVisible = true,
            )
        }
    }

    fun dismissPreview() {
        _state.update { it.copy(previewSheetVisible = false, previewFileName = null, previewUrl = null) }
    }

    fun showQrSheet() {
        _state.update { it.copy(qrSheetVisible = true) }
    }

    fun dismissQrSheet() {
        _state.update { it.copy(qrSheetVisible = false) }
    }

    fun showInfoSheet() {
        _state.update { it.copy(infoSheetVisible = true) }
    }

    fun dismissInfoSheet() {
        _state.update { it.copy(infoSheetVisible = false) }
    }

    fun handleScannedSession(rawValue: String) {
        onSessionIdChanged(rawValue)
        showMessage("Session loaded from QR code")
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun observeSession(sessionId: String) {
        sessionObservationJob?.cancel()
        sessionObservationJob = scope.launch {
            repository.observeSession(sessionId).collectLatest { session ->
                _state.update {
                    it.copy(
                        text = if (pendingTextSessionId == sessionId && pendingText != null) {
                            it.text
                        } else {
                            session.text
                        },
                        files = session.files,
                    )
                }
            }
        }
    }

    private fun flushPendingText() {
        val sessionId = pendingTextSessionId ?: return
        val text = pendingText ?: return
        textSyncJob?.cancel()
        textSyncJob = scope.launch {
            runCatching { repository.updateText(sessionId, text) }
                .onSuccess {
                    if (pendingTextSessionId == sessionId && pendingText == text) {
                        pendingTextSessionId = null
                        pendingText = null
                    }
                }
                .onFailure { showMessage("Failed to sync text") }
        }
    }

    private fun updateSessionVisibility() {
        _state.update { it.copy(sessionVisible = it.sessionId.length > 10) }
    }

    private fun normalizeSessionId(rawValue: String): String {
        val cleaned = rawValue.removePrefix(FirebaseConfig.shareBaseUrl).trim()
        return if (cleaned.isBlank()) generateSessionId() else cleaned
    }

    private fun generateSessionId(length: Int = 12): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString {
            repeat(length) {
                append(characters[Random.nextInt(characters.length)])
            }
        }
    }

    private fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }
}

@Composable
fun rememberSyncBinController(platformContext: PlatformContext): SyncBinController {
    return remember(platformContext) {
        SyncBinController(platformContext = platformContext)
    }
}
