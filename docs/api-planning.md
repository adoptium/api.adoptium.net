
# Learnings from v3 api

## What could be better about the V3 api
- Updater times are slow, especially full updates
  - Treating updates as atomic/monolithic is quite limiting and leads to the slow times
- Process of discovering newly created builds has been flakey and at times quite a long lag between
  builds being produced and turning up in the api. Frequently requiring manual intervention (normally
  due to bugs).
- Document the application and how to develop/contribute better.
- Improve testing and ability for people to run a local test without a full update
- We have had failed requests and been unable to determine why due to a lack of visibility
- Pagination is not well implemented
    - no indication of if next page exists
    - no count of pages
    - no link to next page
- Semver does not fit the Java version scheme and should not be used
- Need to encapsulate "brand" of binary for when vendors ship multiple binaries
- Need to differentiate "nightly" from "EA" builds
- Need to represent builds that contain specific features (i.e with javafx, debug build)
- Represent builds that are under active support
- ImageType represents quite a broad range of things, both the type of jvm packaging and binary type
- In many ways the way we structure our data is an encapsulation of adopts distribution model
- Represent vendor specific version information (i.e openj9 add additional version information and zulu have
  their own version format)
- Separate arch and bitness
- Make LTS more fine grained rather than just true/false
  - Make it vendor specific

# Marketplace Requirements

TODO

# New Website Requirements

TODO

# Decisions
## Should we offer bulk data dumps that can be downloaded and sifted through locally
## Should GraphQl be a first class citizen?

At present, no.

The additional complexity of supporting & maintaining a GraphQL API doesn't appear to provide many benefits.
The Adoptium API implements by a set of RESTful endpoints provided by a single web service and database.

If and when the Adoptium API evolves to become more complex, with data served from multiple downstream services, then we may benefit from 
a layer of abstraction such as that provided by a GraphQL API but even then, an API gateway / proxy may be sufficient for our needs.

## Where should the API be hosted
## What will be our update cycle model
- Consider an event based update model

## Telemetry ( i.e. Metrics / Traces  / Logs)

We should ensure that we are collecting and storing useful telemetry for the deployment of the API.

Assuming the API will still be fronted by Cloudflare, we should ensure that the Cloudflare RayID is forwarded with all
requests and logs are annotated with the ID to ease debugging and correlation when issues occur.

We should ensure that access to the monitoring services is provided to key contributors to ensure they are able to effectively
assist with debugging and analysis of issues.

# Terms
- Asset: An archive/binary
- Vendor: An organisation that compiles/packages an Asset (i.e Adopt, Oracle, Azul, Bellsoft, Amazon etc)
- Flavour/Brand: The brand name of the asset (such as Dragonwell, Zulu) 
- JVM Implementation: The implementation of the JVM (hotspot/openj9)
- Version: version information of the asset
- Feature: A optional feature that a JVM may contain (such as JavaFX, JFR)
- Project: A project such as valhalla, metropolis, loom

# Schema

Types of assets we deliver:
- JVM archives (i.e zip/tar.gz)
- Installers (i.e msi)
- Source code bundles
- Checksums/signatures

