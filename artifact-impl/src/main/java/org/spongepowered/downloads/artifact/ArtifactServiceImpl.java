package org.spongepowered.downloads.artifact;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactService;

public class ArtifactServiceImpl implements ArtifactService {

    private final PersistentEntityRegistry registry;

    @Inject
    public ArtifactServiceImpl(final PersistentEntityRegistry registry) {
        this.registry = registry;
    }


    @Override
    public ServiceCall<NotUsed, List<Artifact>> getArtifacts(String groupId) {
        return null;
    }
}
