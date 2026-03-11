package com.media3watch.sdk.model

/**
 * Sealed result type for [com.media3watch.sdk.transport.HttpSender.send].
 *
 * Distinguishes between outcomes that are worth retrying (transient) vs. those that are
 * permanent client-side errors and should never be queued for offline retry.
 */
internal sealed class SendResult {
    /** The request was accepted by the server (2xx). */
    data object Success : SendResult()

    /**
     * A transient failure that may succeed on a future attempt.
     *
     * Examples: network unavailable, timeout, 5xx server errors, 408 (Request Timeout),
     * 429 (Too Many Requests).
     */
    data class RetryableFailure(val cause: Throwable) : SendResult()

    /**
     * A permanent failure where retrying the same payload will never succeed.
     *
     * Examples: 400 (invalid schema), 401 (bad API key), 403 (forbidden), 404 (wrong endpoint),
     * malformed URL, missing INTERNET permission.
     */
    data class NonRetryableFailure(val cause: Throwable) : SendResult()
}