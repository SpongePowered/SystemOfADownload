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
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistrationResponse;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistrationResponse;
import org.spongepowered.downloads.artifact.api.query.RegisterArtifactRequest;
import org.spongepowered.downloads.artifact.api.query.RegisterGroupRequest;

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
    public ServiceCall<RegisterArtifactRequest, ArtifactRegistrationResponse> registerArtifact(
        final String groupId
    ) {
        return request -> {
            LOGGER.log(Level.DEBUG, String.format("Requesting registration of artifact for group {%s} with info %s", groupId, request));
            return this.getArtifactEntity()
                .ask(new ArtifactCommand.RegisterArtifactCommand(groupId, request.artifactId(), request.version()));
        };
    }

    @Override
    public ServiceCall<RegisterGroupRequest, GroupRegistrationResponse> registerGroup() {
        return null;
    }

    private PersistentEntityRef<ArtifactCommand> getArtifactEntity() {
        return this.registry.refFor(ArtifactEntity.class, ArtifactServiceImpl.ENTITY_KEY);
    }
}
