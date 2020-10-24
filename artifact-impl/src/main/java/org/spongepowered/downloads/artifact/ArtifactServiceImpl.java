package org.spongepowered.downloads.artifact;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.collection.List;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.registration.ArtifactRegistrationResponse;
import org.spongepowered.downloads.artifact.api.registration.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.registration.GroupRegistrationResponse;
import org.spongepowered.downloads.artifact.api.registration.RegisterArtifactRequest;
import org.spongepowered.downloads.artifact.api.registration.RegisterGroupRequest;

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
            return this.getArtifactEntity()
                .ask(new ArtifactCommand.GetArtifact(groupId));
        };
    }

    @Override
    public ServiceCall<RegisterArtifactRequest, ArtifactRegistrationResponse> registerArtifact(
        final String groupId
    ) {
        return null;
    }

    @Override
    public ServiceCall<RegisterGroupRequest, GroupRegistrationResponse> registerGroup() {
        return null;
    }

    private PersistentEntityRef<ArtifactCommand> getArtifactEntity() {
        return this.registry.refFor(ArtifactEntity.class, ArtifactServiceImpl.ENTITY_KEY);
    }
}
