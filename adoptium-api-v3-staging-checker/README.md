# Staging Live Checker

Compares responses from the staging API against the live API to verify they are in sync.

## Build

From the project root (the staging checker requires the `staging-checker` profile):

```bash
./mvnw package -Pstaging-checker -pl adoptium-api-v3-staging-checker -am -DskipTests
```

## Run

```bash
java -cp adoptium-api-v3-staging-checker/target/adoptium-api-v3-staging-checker-3.0.1-SNAPSHOT-jar-with-dependencies.jar \
  net.adoptium.api.v3.checker.StagingLiveChecker
```

## Usage

```
Usage: StagingLiveChecker [vendor] [options]

Vendor (first positional argument):
  adoptium       Use Adoptium staging/live URLs (default)
  adoptopenjdk   Use AdoptOpenJDK staging/live URLs

Options:
  --staging-url <url>    Override the staging API base URL
  --live-url <url>       Override the live API base URL
  --versions <v1,v2,...>  Comma-separated list of Java versions to check
                         (default: 8,11,17,21,22,23,24,25)
  --help, -h             Show this help message and exit
```

## Examples

Check Adoptium with default versions:

```bash
java -cp ...jar net.adoptium.api.v3.checker.StagingLiveChecker
```

Check AdoptOpenJDK:

```bash
java -cp ...jar net.adoptium.api.v3.checker.StagingLiveChecker adoptopenjdk
```

Check specific versions only:

```bash
java -cp ...jar net.adoptium.api.v3.checker.StagingLiveChecker adoptium --versions 11,17,21
```

Use custom staging and live URLs:

```bash
java -cp ...jar net.adoptium.api.v3.checker.StagingLiveChecker \
  --staging-url https://my-staging.example.com \
  --live-url https://my-live.example.com
```

## Exit Codes

- `0` — All URL checks passed.
- `1` — One or more URL checks failed. Failed URLs are listed in the summary output.

