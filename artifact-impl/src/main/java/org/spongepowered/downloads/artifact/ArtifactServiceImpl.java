package org.spongepowered.downloads.artifact;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.group.GroupEntity;

public class ArtifactServiceImpl implements ArtifactService {


    private static final String ENTITY_KEY = ArtifactServiceImpl.class.getName();
    private static final Logger LOGGER = LogManager.getLogger(ArtifactServiceImpl.class);
    private final PersistentEntityRegistry registry;

    @Inject
    public ArtifactServiceImpl(final PersistentEntityRegistry registry) {
        this.registry = registry;
        this.registry.register(ArtifactEntity.class);
    }

    @Override
    public ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(final String groupId) {
        return none -> {
            LOGGER.log(Level.DEBUG, String.format("Requesting artifacts for group id: %s", groupId));
            return this.getArtifactEntity()
                .ask(new ArtifactCommand.GetArtifacts(groupId));
        };
    }

    @Override
    public ServiceCall<ArtifactRegistration.RegisterArtifactRequest, ArtifactRegistration.Response> registerArtifact(
        final String groupId
    ) {
        return request -> {
            LOGGER.log(Level.DEBUG, String.format("Requesting registration of artifact for group {%s} with info %s", groupId, request));
            return this.getArtifactEntity()
                .ask(new ArtifactCommand.RegisterArtifactCommand(groupId, request.artifactId(), request.version()));
        };
    }

    @Override
    public ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup() {
        return null;
    }

    @Override
    public ServiceCall<NotUsed, GroupResponse> getGroup(final String groupId) {
        return notUsed -> {
            LOGGER.log(Level.DEBUG, String.format("Requesting group by id: %s", groupId));
            return this.getGroupEntity(groupId)
                .ask(new GroupEntity.GroupCommand.GetGroup(groupId));
        };
    }

    private PersistentEntityRef<GroupEntity.GroupCommand> getGroupEntity(final String groupId) {
        return this.registry.refFor(GroupEntity.class, groupId);
    }

    private PersistentEntityRef<ArtifactCommand> getArtifactEntity() {
        return this.registry.refFor(ArtifactEntity.class, ArtifactServiceImpl.ENTITY_KEY);
    }
}
