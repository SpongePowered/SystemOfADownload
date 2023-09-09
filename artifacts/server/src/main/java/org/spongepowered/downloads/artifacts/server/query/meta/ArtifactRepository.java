package org.spongepowered.downloads.artifacts.server.query.meta;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import org.spongepowered.downloads.artifacts.server.query.meta.domain.JpaArtifact;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface ArtifactRepository extends ReactiveStreamsCrudRepository<JpaArtifact, Integer> {

    @NonNull
    List<String> findArtifactIdByGroupId(@NonNull String groupId);

    @NonNull
    Mono<JpaArtifact> findByGroupIdAndArtifactId(String groupId, String artifactId);
}
