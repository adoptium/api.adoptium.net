# Adoptium marketplace

This repo contains:

- adoptium-marketplace-schema 
  - Schema definition for vendors to advertise their binaries
- adoptium-marketplace-client
  - Client library for reading a repository with vendor data
- adoptium-marketplace-server
  - Implementation of the adoptium marketplace API 
- exampleRepositories
  - Examples of a vendor repository 


# Build

Build with

```shell
../mvnw clean install
```

# Testing

Tests rely on the data inside the `exampleRepositories` directory in order for tests to pass they must be signed. If 
you wish to modify test assets they need to be re-signed once they have been modified. The procedure would be as follows:

- Generate test keys
    - Look in the `exampleRepositories/keys` directory for scripts that detail generating keys
- Re-sign assets
    - Run `SignTestAssets` in the `adoptium-marketplace-utils` project.

