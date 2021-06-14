# Structure

The AdoptOpenJDK API has 2 main components:

1. Updater - Pulls data from a number of sources such as Github and Docker, parses the data and stores it to the DB.
1. Front-end -  Serve up responses to web requests at <https://api.adoptopenjdk.net>.

## Architecture Diagrams

The diagrams have been created using the [C4 model](https://c4model.com/) with <http://diagrams.net/> .

### Context

![context](./adoptopenjdk-api-architecture-context.svg)

### Container

![container](./adoptopenjdk-api-architecture-container.svg)

## Updater

The updater periodically polls for new data, and stores that data into the DB. There are 2 types of refresh:
1. Full
    - A full refresh of all data from the various sources
    - Performed when the updater starts and then every 24 hours there-after. 
1. Incremental
    - A refresh of only newly added or modified files.
    - Performed every 15 min
 
The sources of data for the api are:

### Github
The binary repositories such as:
 - https://github.com/AdoptOpenJDK/openjdk8-binaries
 - https://github.com/AdoptOpenJDK/openjdk11-binaries 

Each of these repos contains a number of releases, inside each release are a number of assets in general for each asset there is:
- Binary archive (i.e OpenJDK11U-jdk_x64_linux_hotspot_2020-05-02-13-03.tar.gz)
- Metadata file (i.e OpenJDK11U-jdk_x64_linux_hotspot_2020-05-02-13-03.tar.gz.json)
- Checksum (i.e OpenJDK11U-jdk_x64_linux_hotspot_2020-05-02-13-03.tar.gz.sha256.txt)

The updater interacts with the Github api using the [V4 GraphQL interface](https://developer.github.com/v4/guides/intro-to-graphql/).
Once we have obtained the data through the Github api the Upstream (for the upstream OpenJDK project) and Adopt mappers which map the
Github data into Adopt API models. It does this by iterating through the list of repos and releases, for each binary asset download 
its metadata or checksum and parse their contents in order to extract the data. If metadata is not available then we attempt to extract 
the relevant data by parsing the file name.

In order to speed up access and reduce bandwidth we use Mongo as a cache for this data. When we require data such as the metadata file
or checksum, that data will be provided by the cache (assuming it is present), and an asynchronous refresh of that data will be scheduled
to make sure it is up to date.

### DockerHub
The DockerHub repositories are only required for displaying stats. We pull data from the DockerHub API inside DockerStatsInterface.

### Running
To run the updater tool:
 - generate the artifacts by running `mvnw clean install`. 
 - `cd` into the `adoptopenjdk-api-v3-updater` directory
 - run `java -jar ./target/adoptopenjdk-api-v3-updater-3.0.0-SNAPSHOT-jar-with-dependencies.jar`

### Database
The database stores 3 main types of data:
1. Release - The raw binary data extracted from Github
1. Stats - Download statistics. Updated at the end of a full refresh.
    - DockerStats - Broken down into each docker repository
    - GitHubStats - Broken down into each feature version
1. Web-Cache - Cached data used to speed up requests

## Front-end

The front-end is a Quarkus application that uses OpenAPI for documentation. Data is polled from the database into memory and requests are then
serviced from that dataset.

### Examples

Fetch binaries and installers:
- https://api.adoptopenjdk.net/v3/binary/latest/11/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk
- https://api.adoptopenjdk.net/v3/installer/latest/14/ea/linux/s390x/jre/openj9/large/adoptopenjdk

Raw asset data:
- https://api.adoptopenjdk.net/v3/assets/feature_releases/11/ga
- https://api.adoptopenjdk.net/v3/assets/feature_releases/14/ea?architecture=s390x&jvm_impl=openj9
- https://api.adoptopenjdk.net/v3/assets/latest/8/openj9
- https://api.adoptopenjdk.net/v3/assets/version/11.0.4+11.1

Release info:
- https://api.adoptopenjdk.net/v3/info/available_releases
- https://api.adoptopenjdk.net/v3/info/release_names
- https://api.adoptopenjdk.net/v3/info/release_names?page_size=20&release_type=ga
- https://api.adoptopenjdk.net/v3/info/release_versions

Download statistics:
- https://api.adoptopenjdk.net/v3/stats/downloads/total
- https://api.adoptopenjdk.net/v3/stats/downloads/tracking
- https://api.adoptopenjdk.net/v3/stats/downloads/tracking?source=dockerhub&feature_version=11
- https://api.adoptopenjdk.net/v3/stats/downloads/monthly

A full list of endpoints and each of the parameters can be found at https://api.adoptopenjdk.net/swagger-ui/

### Running
To run the front-end quarkus tool, `cd` into the `adoptopenjdk-api-v3-frontend` directory and run `../../mvnw quarkus:dev`. This will then run the tool on port 8080.
NOTE: You will need to have let the Updater run a full cycle before any data is shown.
