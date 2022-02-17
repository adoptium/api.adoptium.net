package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/***
 * Index file that vendors will publish. These provide links to either further index files or release files that contain a ReleaseList.
 *
 * i.e:
 *
 * ```
 * {
 *     "indexes": [
 *         "8/index.json",
 *         "17/index.json"
 *     ],
 *     "releases": [
 *         "11/11.0.14+9.json"
 *     ]
 * }
 * ```
 */
@Schema
public class IndexFile {

    // Relative path to another index file, links to further index files
    private final List<String> indexes;

    // Relative path to release file, these will contain data as defined by ReleaseList
    private final List<String> releases;

    @JsonCreator
    public IndexFile(
            @JsonProperty(value = "indexes") List<String> indexes,
            @JsonProperty(value = "releases") List<String> releases) {
        this.indexes = indexes;
        this.releases = releases;
    }

    public List<String> getIndexes() {
        return indexes;
    }

    public List<String> getReleases() {
        return releases;
    }
}
