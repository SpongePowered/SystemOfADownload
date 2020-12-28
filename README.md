# SystemOfADownload

![Actions Status](https://github.com/SpongePowered/SystemOfADownload/workflows/.github/workflows/build-project.yaml/badge.svg)

The metadata generator webapp that serves up enhanced information
and "tagging" for artifacts from a Maven repository. This is intended to
serve as a backend to power the data to generate for serving an enhanced
downloads website.

## Requirements

- Java 15

## Technologies in use

### Framework

SystemOfADownload (SOAD) is built on [LagomFramework], an opinionated
[Event Source] + [CQRS] architecture framework built on [Play], and as such relies
on several functional programing paradigms. Lagom as a whole provides enough
to build out several services with semi-automatic service discovery routing
and using [Cassandra] as the primary storage database for the Event Journal.

To learn about the topics, please visit
[Lagom's documentation on concepts](https://www.lagomframework.com/documentation/1.6.x/java/CoreConcepts.html)
that goes at length about how the system works together.

### Services
Each service (maven module) in this project is meant to represent a specific
domain of control/knowledge, and as such, rendered into its own service. They are
as follows:
- `ArtifactService`
- `CommitService`
- `ChangelogService`
- `SonatypeWebhookService`
- `GraphQLService`

The first three are what effectively being given as model views to exploring a paired
[Sonatype Nexus] repository instance for artifacts and presenting/serving them in a
more user friendlier way by providing git-like changelogs between artifacts. 

#### ArtifactService

The bread and butter of the shebang. Manages/creates/caches artifacts to knowledge by
[maven coordindates](https://maven.apache.org/pom.html#Maven_Coordinates). Typically,
an `Artifact` is not actually an artifact, but considered a `Component` with several
`Asset`s. Here we have exposed the ability to retrieve an artifact (if registered) and
its known assets along with download url's provided.

#### CommitService

This is a little more subtle, but effectively, since each artifact may or may not have a
`Git-Commit` listed in the jar manifest, this service strictly deals with managing registered
repositories, updating them, and pulling the list of commits diffing between two commits.

#### ChangelogService

Amalgamation of information between the `ArtifactService` and `CommitService`. This is where
we can store/manage changelogs per artifact regsitered by an entity.

#### SonatypeWebhookService

This is the webhook functionality that performs a [Saga]-like series of jobs or units of
work. Because the nature of an artifact being uploaded to Sonatype and "the fact that anything
can and will go wrong", 

[LagomFramework]:https://lagomframework.com/
[Event Source]:https://docs.microsoft.com/en-us/azure/architecture/patterns/event-sourcing
[CQRS]:https://docs.microsoft.com/en-us/azure/architecture/patterns/cqrs
[Play]:https://www.playframework.com
[Cassandra]:https://cassandra.apache.org
[Sonatype Nexus]:https://www.sonatype.com/nexus/repository-pro
[Saga]:https://docs.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga
