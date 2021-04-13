# 5. Update Paging

Date: 2021-03-31

## Status

Proposed

## Context

Paging in the past has not been well supported. We do not report next links or counts of the number of pages,
previously users had to keep on reading pages until they hit a 404.

## Decision

Add [Link](https://tools.ietf.org/html/rfc5988#section-5.5) header on the returned page that provides links to 
the next and last page, i.e:

```
<https://api.adoptopenjdk.net/v3/assets/feature_releases/8/ga?page=3&page_size=10>; rel="next", 
<https://api.adoptopenjdk.net/v3/assets/feature_releases/8/ga?page=99&page_size=10>; rel="last"
```

## Consequences
Users can follow the next link until it is no longer present, or they can check their current page
against the last to see if they are at the end.
