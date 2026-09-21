package com.kashif_e.backdrop

/** Runs [block] inside a Compose trace section named [sectionName]. */
internal inline fun <R> trace(sectionName: String, block: () -> R): R {
    return androidx.compose.ui.util.trace(sectionName, block)
}

/** Runs [block]; Skiko does not currently emit an asynchronous platform trace section. */
internal suspend inline fun <R> traceAsync(
    sectionName: String,
    cookie: Int,
    crossinline block: suspend () -> R,
): R = block()
