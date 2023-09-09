package org.spongepowered.downloads.artifacts.server.query.meta;

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
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import reactor.core.publisher.Mono;

import java.util.List;

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

    @Get(value = "/{artifactId}",
        produces = MediaType.APPLICATION_JSON
    )
    @Status(HttpStatus.OK)
    public Mono<GetArtifactsResponse> getArtifacts(
        final @PathVariable String groupID,
        final @PathVariable String artifactId
    ) {
        return this.artifactsRepo.findByGroupIdAndArtifactId(groupID, artifactId)
            .<GetArtifactsResponse>map(a -> new GetArtifactsResponse.ArtifactsAvailable(List.of(a.getArtifactId())))
            .onErrorReturn(new GetArtifactsResponse.GroupMissing(groupID));
    }
}
