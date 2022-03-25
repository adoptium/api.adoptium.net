package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class ReleaseUpdateInfo {
    private final ReleaseList added;
    private final ReleaseList updated;
    private final ReleaseList removed;
    private final Date timestamp;

    @JsonCreator
    public ReleaseUpdateInfo(
        @JsonProperty("added") ReleaseList added,
        @JsonProperty("updated") ReleaseList updated,
        @JsonProperty("removed") ReleaseList removed,
        @JsonProperty("timestamp") Date timestamp
    ) {
        this.added = added;
        this.updated = updated;
        this.removed = removed;
        this.timestamp = timestamp;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public ReleaseList getRemoved() {
        return removed;
    }

    public ReleaseList getUpdated() {
        return updated;
    }

    public ReleaseList getAdded() {
        return added;
    }
}
