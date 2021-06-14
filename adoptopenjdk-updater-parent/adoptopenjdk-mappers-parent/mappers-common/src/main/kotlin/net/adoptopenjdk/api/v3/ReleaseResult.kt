package net.adoptopenjdk.api.v3

import net.adoptopenjdk.api.v3.models.Release

class ReleaseResult(val result: List<Release>? = null, val error: String? = null) {
    fun succeeded() = error == null && result != null
}
