package net.adoptium.api.v3

import net.adoptium.api.v3.models.Release

/** A successful (possibly empty) snapshot, a retryable failure, or an explicit permanent exclusion. */
class ReleaseResult(val result: List<Release>? = null, val error: String? = null, val retryable: Boolean = result == null || error != null) {
    fun succeeded() = error == null && result != null
}
