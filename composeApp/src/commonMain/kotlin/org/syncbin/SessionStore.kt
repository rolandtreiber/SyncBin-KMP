package org.syncbin

import androidx.compose.runtime.Composable

interface SessionStore {
    fun loadCurrentSessionId(): String?
    fun saveCurrentSessionId(sessionId: String)
    fun loadQuickAccess(): List<String>
    fun saveQuickAccess(sessionIds: List<String>)
}

expect class PlatformContext

@Composable
expect fun rememberPlatformContext(): PlatformContext

expect fun createSessionStore(platformContext: PlatformContext): SessionStore
