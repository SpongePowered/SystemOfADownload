# SystemOfADownloads

The metadata generator webapp that serves up enhanced information
and "tagging" for artifacts from a Maven repository. This is intended to
serve as a backend to power the data to generate for serving an enhanced
downloads website.

## Requirements

- Java 15
- PostresQL 13

## Technologies in use

### API

#### GraphQL

We first and foremost will expose a GraphQL endpoint to serve as the
primary data retrieval for querying changelogs between artifacts on 
the download website. The API will be documented on the wiki of this
repository, and hopefully the webapp itself can generate its own changelogs
between releases.

#### RestAPI

This will be a secondary feature of the webapp, allowing to expose the
updates of releases to server administrators/hosting providers.