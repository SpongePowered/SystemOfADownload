# Versions Worker

Versions Worker is a collection of workers that perform versioned artifact introspection.
The primary use case is to organize all of the reactive pieces of work that come about from
"a new version is registered". The workers are designed to be pluggable, and the
[`VersionsWorkerSupervisor`](VersionsWorkerSupervisor.java) is the main entry point for this
side of the system. 

## Overview

While a normal Lagom application is a single service with a single Guice module, the
Versions Worker has a separate paired module to initialize the guardian of the child worker
actors. There is an additional side effect that is gained out of this: the supervisor is able
to reference the VersionsService indirectly as a consumer, and therefore subscribe to the
[`VersionsService.artifactUpdateTopic()`](./../../../../../../../../../versions-api/src/main/java/org/spongepowered/downloads/versions/api/VersionsService.java) topic.
An advantage as well is that while each binary of the workers are capable of delegating their
work to avoid blocking eachother, the VersionsService remains active for the primitive information
as a query reference.

## Interfaces between systems

### Kafka

Through heavy use of Akka Clustering and Akka remoting, we can spin off specific workloads to
specific instances, such as git commit resolution, or artifact binary downloading and processing,
or changelog parsing. To safely coordinate the flow of work and prevent soft failures, we rely heavily
on the TopicSubscriber pattern to have the assurance the work will be done, even if the actors/binaries
restart for any reason. Likewise, we can coordinate the flow of work to single instances for longer
running jobs, while allowing for rapid fire handling of "basic" data being transformed and
messages sent off.

### ReadSideProcessors

We take advantage of EventSourceBased actors to describe the state of the various entities (such as
a versioned artifact with assets not having a git commit, to becoming a git commit hash extracted
from the binary, to extracting the commit details from the git repository, to resolving the changes
between two ordinal versions) and take advantage that we can populate our database with the important
details from the various events, therefor enabling the Query service to simply query for the state of
whatever models it is interested in.

