package org.spongepowered.downloads.artifacts.server.cmd.group;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;
import org.spongepowered.downloads.artifacts.server.cmd.group.domain.Artifact;
import org.spongepowered.downloads.artifacts.server.cmd.group.domain.Group;

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface ArtifactRepository extends CrudRepository<Artifact, Long> {
    boolean existsByArtifactIdAndGroup(String artifactId, Group group);
}
