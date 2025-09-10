package net.adoptium.api.v3

import net.adoptium.api.v3.models.Attestation

class AttestationResult(val result: List<Attestation>? = null, val error: String? = null) {
    fun succeeded() = error == null && result != null
}
