# Contributing to Adoptium API (V3)

## Overview

The Adoptium API V3 is a Kotlin application (fronted by Swagger and OpenAPI) which makes
calls via the GitHub API in order to retrieve Adoptium binaries and metadata.

Since the GitHub API is rate limited we use MongoDB as a caching mechanism.

## Source code management & branching

There are two main branches in the project:

- `main`
- `production`

All contributions should be made by forking the project and raising a pull request (PR) against the `main` branch.

The `main` branch represents the current live state of the [Staging environment](https://staging-api.adoptium.net/).

The `production` branch represents the current live state of the [Production environment](https://api.adoptium.net/).

For more details related to deployment of the API, see [the deployment section](#deployment--continuous-deployment-cd) of this guide.

## Build

### Pre-Requisites

[Java 17](https://adoptium.net/releases.html?variant=openjdk17) is a requirement to build the project.

### Optional Set-up

If you want to use the updater tool to add entries into the database, you need to generate a github token, which can be done [here](https://github.com/settings/tokens). It doesn't need any specific permissions. Once you have a token generated, create a file at ~/.adopt_api/token.properties and type `token=your-token-here`

The production server uses mongodb to store data, however you can also use Fongo. If you would like to install mongodb and are on mac, I used this [guide](https://zellwk.com/blog/install-mongodb/) which utilizes homebrew. You can also install `mongo` which is a command-line tool that gives you access to your mongodb, allowing you to manually search through the database.

### GitHub App Authentication

The updater can be used with a GitHub Token or GitHub App. To use a GitHub app you need to generate an app on GitHub. Once you've done that you need to convert the key to PKCS#8 format using the following command:

```bash
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in your-rsa-private-key.pem -out pkcs8-key.pem
```

Once this is done you can export the following variables at runtime:

```bash
export GITHUB_APP_ID="1234"
export GITHUB_APP_INSTALLATION_ID="1234"
export GITHUB_APP_PRIVATE_KEY=$'-----BEGIN PRIVATE KEY-----
<key contents>
-----END PRIVATE KEY-----'
```

### Build Tool

[Maven](https://maven.apache.org/index.html) is used to build the project.

We use the [Maven Wrapper](https://github.com/takari/maven-wrapper) (`mvnw`) to ensure that there's a consistent, repeatable build.

The [POM File](./pom.xml) is the place to start.

**NOTE:** We use a multi-module project structure. The root level [Maven POM](./pom.xml) contains the majority
of the configuration that the children inherit from.

### Build Command

Builds can be targeted to build for Adoptium or AdoptOpenJDK, these are chosen by activating either the `adoptium`
or `adoptopenjdk` profiles.

To perform a full build and test you run the following:

`./mvnw clean install -Padoptium`

If you wish to view all the Maven reporting about the project then after running the above you can run the following:

`./mvnw site -Padoptium`

*WARN*: `./mvnw clean install site -Padoptium` does not work at time of writing due to an order of operations problem

If you wish to produce an AdoptOpenJDK ecosystem build, use `-Padoptopenjdk` i.e.

`./mvnw clean install -Padoptopenjdk`

### Docker

For convenience, you can build the API components with `Docker` and `docker-compose`.

```bash
docker-compose build
```

Using a multi-stage [Dockerfile](Dockerfile) build, a Docker image is created that supports running both the updater and the front-end.

The [docker-compose.yml](docker-compose.yml) defines a service for each component, as well a dependency on MongoDB, allowing you to spin up the full stack required for the API.

You just need to provide your GitHub access token as an environment variable and run `docker-compose up`. For example:

```bash
export GITHUB_TOKEN=your-personal-github-token
docker-compose up
```

You will need to wait the updater to complete its first full run before the API is usable. There is currently no persistence between runs.

The front-end app will be available at <http://localhost:8080>.

You can connect to the MongoDB instance using your Mongo client at <mongodb://localhost:27017>.

## Code Style

### ktlint

The project applies the [ktlint](https://github.com/pinterest/ktlint) linter to provide an opinionated set of rules that reflect the official Kotlin code style.

`ktlint` is configured in the top-level Maven [POM](./pom.xml) and executed during the `validate` phase of the Maven life-cycle.

Refer to `ktlint`'s [docs](https://github.com/pinterest/ktlint#-with-maven) for more info.

## Testing

**WARN** This API is critical to the success of Adoptium therefore it is
essential that tests are provided for all new functionality.

### Code Coverage

Code coverage metrics are collected using [JaCoCo](https://www.jacoco.org/jacoco/) and configured via a plugin in the top-level [Maven POM](./pom.xml).

## Continuous Integration (CI)

### Pull Requests

There is a [GitHub Action](.github\workflows\build.yml) file which the CI system
in GitHub uses to build and test a Pull Request.

**NOTE:** Please update the dependencies in this file if you have changed the versions of:

- The JDK
- openapi-generator-maven-plugin  

## API Definition and Usage

We use Swagger to document the API. The Swagger documentation can be viewed at: [swagger-ui](https://api.adoptium.net/swagger-ui).
The Open API definition for this can be viewed at [openapi](https://api.adoptium.net/openapi).

## Deployment / Continuous Deployment (CD)

You can choose to deploy this API where you wish, for Adoptium we use Continuous Deployment.

### Adoptium

For Adoptium, this API deploys to Microsoft Azure.

The `production` branch is synchronized with `main` (via a pull request that applies all outstanding commits from `main` to `production`) to perform a release of the latest API changes to the Production environment.  

A build and deploy then happens as governed by the [Build Docker](./.github/workflows/build-docker.yml) and [Publish](./.github/workflows/publish.yml) files.

### AdoptOpenJDK

For AdoptOpenJDK, this API deploys to Red Hat OpenShift and is front ended by [Cloudflare](https://www.cloudflare.com) as a CDN.

The `production` branch is synchronized with `main` (via a pull request that applies all outstanding commits from `main` to `production`) to perform a release of the latest API changes to the Production OpenShift environment.  

A build then happens in OpenShift as governed by the [Source To Image (s2i) Assembly file](./.s2i/bin/assemble) file.

## Code Architecture and Code

The Adoptium API V3 is a Kotlin application (fronted by Swagger and OpenAPI) which makes
calls via the GitHub API in order to retrieve Adoptium binaries and metadata.

Since the GitHub API is rate limited we use MongoDB as a caching mechanism.

See [Code Structure](./docs/STRUCTURE.md) doc for more details.

### Architecture Decisions Records

For any significant project or architectural changes we use Architecture Decision Records (ADRs) to capture them.

We manage ADRs using [adr-tools](https://github.com/npryce/adr-tools) in the [docs/adr](docs/adr) directory.

## Common Tasks

In this section we list some common tasks and where to start.

### I want support a new version string

If you need to add/edit/remove a supported version string then you need to update the [VersionParser](./adoptium-models-parent/adoptium-api-v3-models/src/main/kotlin/net/adoptium/api/v3/parser/VersionParser.kt) and its corresponding [VersionParserTest](./adoptium-models-parent/adoptium-api-v3-models/src/test/kotlin/api/VersionParserTest.kt).

### I want to add a new variant such as OpenJDK's project amber

You'll need to start at the [Platforms JSON](adoptium-api-v3-frontend/src/main/resources/JSON/platforms.json) and
[Variants JSON](adoptium-api-v3-frontend/src/main/resources/JSON/variants.json).

### I want to upgrade the Maven version

```bash
mvn -N wrapper:wrapper -Dmaven=xxx.yyy.zzz
```
e.g. to upgrate to version 3.6.9

```bash
mvn -N wrapper:wrapper -Dmaven=3.9.6
```
