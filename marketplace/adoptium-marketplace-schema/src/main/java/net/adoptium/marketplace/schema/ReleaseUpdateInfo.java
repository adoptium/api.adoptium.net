package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class ReleaseUpdateInfo {
    private final ReleaseList added;
    private final ReleaseList updated;
    private final ReleaseList removed;
    private final Date timestamp;

    private final String errorMessage;

    @JsonCreator
    public ReleaseUpdateInfo(
        @JsonProperty("added") ReleaseList added,
        @JsonProperty("updated") ReleaseList updated,
        @JsonProperty("removed") ReleaseList removed,
        @JsonProperty("timestamp") Date timestamp,
        @JsonProperty("errorMessage") String errorMessage
    ) {
        this.added = added;
        this.updated = updated;
        this.removed = removed;
        this.timestamp = timestamp;
        this.errorMessage = errorMessage;
    }

    public ReleaseUpdateInfo(
        ReleaseList added,
        ReleaseList updated,
        ReleaseList removed,
        Date timestamp
    ) {
        this(added, updated, removed, timestamp, null);
    }

    public ReleaseUpdateInfo(String errorMessage) {
        this(null, null, null, null, errorMessage);
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

    public String getErrorMessage() {
        return errorMessage;
    }
}
