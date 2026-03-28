package org.syncbin

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform