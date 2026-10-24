package org.scrollia.app.api

/** The server answered with a non-2xx status. */
class HttpStatusException(val code: Int) : Exception("HTTP $code")

/** A 2xx answer whose body is not what the client expects. */
class MalformedResponseException(cause: Throwable?) : Exception("Malformed response", cause)
