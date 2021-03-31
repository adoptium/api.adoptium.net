# 4. Remove Semver From the API

Date: 2021-03-31

## Status

Proposed

## Context

In the past we have used semver as our main version format. This has in cases led to having to manipulate Java versions
in order to fit them into the semver format. This has been suboptimal and led to hacks to keep to semver.

## Decision

We will for all relevant versions use JEP 322 Java versions in relevant areas.

In cases where vendors add additional version information to their Java version, encapsulate that in a vendor specific
version property.

In the cases where platform specific versions are required, such as if a package manager dictates a version format, encapsulate
that is an additional property.

