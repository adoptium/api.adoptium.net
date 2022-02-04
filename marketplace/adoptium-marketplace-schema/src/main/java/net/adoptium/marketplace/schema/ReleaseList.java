package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema
public class ReleaseList {

    private final List<Release> releases;

    @JsonCreator
    public ReleaseList(
            @JsonProperty("releases") List<Release> releases
    ) {
        this.releases = releases;
    }

    public List<Release> getReleases() {
        return releases;
    }
}
