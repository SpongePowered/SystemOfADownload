package org.spongepowered.downloads.artifacts.server.cmd.group;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;
import org.spongepowered.downloads.artifacts.server.cmd.group.domain.Group;

import java.util.Optional;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface GroupRepository extends CrudRepository<Group, Long> {
    Optional<Group> findByGroupId(String groupId);
}
