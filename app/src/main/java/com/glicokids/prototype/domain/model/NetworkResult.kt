package com.glicokids.prototype.domain.model

/**
 * Module 7 — the result of a call to an external web service (nearby health places, food
 * search). [Result] is the existing sealed result type in this package, used by
 * [com.glicokids.prototype.domain.usecase.CalculateBolusUseCase]; it was considered for this
 * layer too, and rejected on purpose rather than reused by reflex.
 *
 * [Result.Failure] folds every failure into one `exception` + optional `message`. That fits a
 * synchronous domain validation (a bad clinical parameter), where the caller only ever needs
 * one message to show. A network call has three failure shapes a screen needs to tell apart to
 * give an honest message — "you are offline", "the service is down right now", "we got an
 * answer back but could not read it" — and [Result] gives no way to do that from the type
 * itself; every call site would have to inspect the wrapped exception by hand, and duplicate
 * that inspection everywhere it happens. [NetworkResult] makes the three cases first-class
 * instead.
 */
sealed class NetworkResult<out T> {

    data class Success<out T>(val data: T) : NetworkResult<T>()

    sealed class Failure : NetworkResult<Nothing>() {

        /** No usable network connection at the moment the call was attempted — checked before
         * the request ever went out. */
        object NoConnection : Failure()

        /** The request reached the network, but the service did not answer usably: a non-2xx
         * status, a timeout, or the connection dropping mid-request. [httpStatusCode] is the
         * status the service returned, when there was one to read. */
        data class ServiceUnavailable(val httpStatusCode: Int? = null) : Failure()

        /** An answer came back, but it did not parse into the model this call promises —
         * malformed JSON/XML or a payload missing a field the parser required. [cause] is the
         * parse failure, kept for logging; callers should not need to inspect it to decide what
         * to show the user. */
        data class UnreadableResponse(val cause: Exception) : Failure()
    }
}
