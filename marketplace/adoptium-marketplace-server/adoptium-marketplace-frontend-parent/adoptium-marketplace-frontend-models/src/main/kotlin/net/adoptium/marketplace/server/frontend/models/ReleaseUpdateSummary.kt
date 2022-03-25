package net.adoptium.marketplace.server.frontend.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

class ReleaseUpdateSummary @JsonCreator constructor(
    @JsonProperty("added") val added: List<String>,
    @JsonProperty("updated") val updated: List<String>,
    @JsonProperty("removed") val removed: List<String>,
    @JsonProperty("timestamp") val timestamp: Date
)
