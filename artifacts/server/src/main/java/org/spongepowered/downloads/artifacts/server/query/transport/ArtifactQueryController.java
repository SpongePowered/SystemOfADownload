package org.spongepowered.downloads.artifacts.server.query.transport;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.SpawnProtocol;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Status;
import jakarta.inject.Inject;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.query.GetArtifactDetailsResponse;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifacts.server.query.meta.ArtifactRepository;
import reactor.core.publisher.Mono;

@Controller("/groups/{groupID}/artifacts")
@Requires("query")
public class ArtifactQueryController {

    private final ClusterSharding sharding;
    private final ActorSystem<SpawnProtocol.Command> system;
    private final ArtifactRepository artifactsRepo;


    @Inject
    public ArtifactQueryController(
        final ClusterSharding sharding,
        final ActorSystem<SpawnProtocol.Command> system,
        final ArtifactRepository artifactsRepo
    ) {

        this.sharding = sharding;
        this.system = system;
        this.artifactsRepo = artifactsRepo;
    }

    @Get(value = "/", produces = MediaType.APPLICATION_JSON)
    @Status(HttpStatus.OK)
    public Mono<GetArtifactsResponse> getArtifacts(
        final @PathVariable String groupID
    ) {
        return this.artifactsRepo.findArtifactIdByGroupId(groupID)
            .collectList()
            .<GetArtifactsResponse>map(GetArtifactsResponse.ArtifactsAvailable::new)
            .onErrorReturn(new GetArtifactsResponse.GroupMissing(groupID));
    }

    /**
     * Get the details of an artifact.
     *
     * @param groupID    The group ID of the artifact
     * @param artifactId The artifact ID of the artifact
     * @return The details of the artifact
     */
    @Get(value = "/{artifactId}",
        produces = MediaType.APPLICATION_JSON
    )
    @Status(HttpStatus.OK)
    public Mono<GetArtifactDetailsResponse> getArtifact(
        final @PathVariable String groupID,
        final @PathVariable String artifactId
    ) {
        return this.artifactsRepo.findByGroupIdAndArtifactId(groupID, artifactId)
            .<GetArtifactDetailsResponse>map(a -> new GetArtifactDetailsResponse.RetrievedArtifact(
                a.coordinates(),
                a.displayName(),
                a.website(),
                a.gitRepo(),
                a.issues(),
                a.tags()
            ))
            .onErrorReturn(new GetArtifactDetailsResponse.MissingArtifact(new ArtifactCoordinates(groupID, artifactId)));
    }
}
