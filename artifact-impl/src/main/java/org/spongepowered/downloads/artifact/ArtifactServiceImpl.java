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
import org.spongepowered.downloads.artifact.api.query.GetVersionsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.collection.ArtifactCollectionEntity;
import org.spongepowered.downloads.artifact.group.GroupEntity;

import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

public class ArtifactServiceImpl implements ArtifactService {

    private static final Logger LOGGER = LogManager.getLogger(ArtifactServiceImpl.class);
    private final PersistentEntityRegistry registry;

    @Inject
    public ArtifactServiceImpl(final PersistentEntityRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ServiceCall<NotUsed, GetArtifactsResponse> getArtifacts(final String groupId) {
        return none -> {
            LOGGER.log(Level.DEBUG, String.format("Requesting artifacts for group id: %s", groupId));
            return this.getGroupEntity(groupId)
                .ask(new GroupEntity.GroupCommand.GetArtifacts(groupId));
        };
    }

    @Override
    public ServiceCall<NotUsed, GetVersionsResponse> getArtifactVersions(
        final String groupId,
        final String artifactId
    ) {
        return notUsed -> {
            LOGGER.log(Level.DEBUG, String.format("Requesting versions for artifact: %s:%s", groupId, artifactId));
            return this.getCollection(groupId + ":" + artifactId)
                .ask(new ArtifactCollectionEntity.Command.GetVersions(groupId, artifactId));
        };
    }

    @Override
    public ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup() {
        return registration -> {
            final String mavenCoordinates = registration.groupCoordinates();
            final String name = registration.groupName();
            final String website = registration.website();
            return this.getGroupEntity(mavenCoordinates)
                .ask(new GroupEntity.GroupCommand.RegisterGroup(mavenCoordinates, name, website));
        };
    }

    @Override
    public ServiceCall<ArtifactRegistration.RegisterCollection, ArtifactRegistration.Response> registerArtifacts() {
        return registration -> {
            final String mavenCoordinates = registration.collection().getMavenCoordinates();
            final StringJoiner joiner = new StringJoiner(",", "[", "]");
            registration.collection().getArtifactComponents().keySet().map(joiner::add);
            LOGGER.log(
                Level.DEBUG,
                String.format("Requesting registration of collection %s with artifacts: %s", mavenCoordinates, joiner)
            );
            final String group = registration.collection().getGroup().getGroupCoordinates();
            final String artifactId = registration.collection().getArtifactId();
            return this.getGroupEntity(group)
                .ask(new GroupEntity.GroupCommand.RegisterArtifact(artifactId))
                .thenCompose(response -> {
                    if (response instanceof ArtifactRegistration.Response.GroupMissing) {
                        return CompletableFuture.completedFuture(response);
                    }
                    return this.getCollection(group + ":" + artifactId)
                        .ask(new ArtifactCollectionEntity.Command.RegisterCollection(registration.collection()))
                        .thenApply(
                            notUsed -> new ArtifactRegistration.Response.RegisteredArtifact(registration.collection()));
                });
        };
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

    private PersistentEntityRef<ArtifactCollectionEntity.Command> getCollection(final String mavenCoordinates) {
        return this.registry.refFor(ArtifactCollectionEntity.class, mavenCoordinates);
    }

}
