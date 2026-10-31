package org.scrollia.app.helpers

import java.io.IOException

/**
 * What a discovery screen shows instead of, or as, its content. Exactly one
 * applies at a time; a failure while items are already shown is an inline footer instead.
 */
enum class ContentState {
    /** No usable items yet and the first request is in flight. */
    LOADING,

    /** Items to show. */
    RESULTS,

    /** The request for the current input succeeded with nothing to show. */
    EMPTY,

    /** No items, and the request could not reach the server. */
    OFFLINE,

    /** No items, and the server's answer was unusable (HTTP error, malformed body...). */
    ERROR
}

/** Why a request failed, as far as the user is concerned. */
enum class LoadFailure {
    OFFLINE, ERROR;

    val contentState: ContentState
        get() = if (this == OFFLINE) ContentState.OFFLINE else ContentState.ERROR

    companion object {
        /** Transport failures (no route, DNS, timeout, reset) mean offline; anything else is an error. */
        fun of(error: Throwable): LoadFailure = if (error is IOException) OFFLINE else ERROR
    }
}
