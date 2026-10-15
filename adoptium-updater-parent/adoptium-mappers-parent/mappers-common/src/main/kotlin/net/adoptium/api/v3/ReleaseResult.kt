package net.adoptium.api.v3

import net.adoptium.api.v3.models.Release

class ReleaseResult(val result: List<Release>? = null, val error: String? = null, val retryable: Boolean = false) {
    fun succeeded() = error == null && result != null
}
