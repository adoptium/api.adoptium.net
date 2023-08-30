
# Adoptium API

**NOTICE:** [AdoptOpenJDK API v3](https://github.com/AdoptOpenJDK/openjdk-api-v3/blob/master/README.md) has been deprecated as part of the move to Eclipse Foundation and rebrand to Adoptium.

## Overview

The Adoptium API provides a way to consume JSON information about the Adoptium Temurin releases and nightly builds.  
Sign up to the [`#api` Slack channel](https://adoptium.net/slack/) where major API updates will be announced, and visit [adoptium.net](https://adoptium.net) to find out more about the community.

## Usage

The API is documented using Swagger.  The Swagger documentation can be viewed at: [api.adoptium.net/swagger-ui](https://api.adoptium.net/swagger-ui).
The Open API spec for this can be viewed at: [api.adoptium.net/openapi](https://api.adoptium.net/openapi)

For more information, including example queries, please look at the [API cookbook](https://github.com/adoptium/api.adoptium.net/blob/main/docs/cookbook.adoc).

## Who's using the Adoptium API?

The Adoptium API (and its predecessor at AdoptOpenJDK) has served over 200 million downloads by a wide variety consumers, from individuals to organizations.

Check the [Download Statistics Dashboard](https://dash.adoptium.net/) for the latest numbers.  

The following list highlights a small subset of consumers and their use-cases:

- [Adoptium Website](https://adoptium.net/) - the API drives the release listings on the AdoptOpenJDK website allowing individuals to download the JDK distribution of their choice.
- [Gradle](https://docs.gradle.org/) - the Gradle project defaults to use the API for its [toolchains](https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning) feature.
- [Update Watcher for Adoptium & AdoptOpenJDK](https://github.com/tushev/aojdk-updatewatcher) - uses the API to automatically manage the JDK installations on an individual's machine.

## Version Bump

When a new version of Java is released, the version at [Versions.kt](adoptium-models-parent/adoptium-api-v3-models/src/main/kotlin/net/adoptium/api/v3/models/Versions.kt),
or by setting the property `LATEST_JAVA_VERSION` on the deployment.

## Development

To learn more about how we build & run the API, check out [CONTRIBUTING.md](CONTRIBUTING.md) and the [FAQs](FAQ.md).
