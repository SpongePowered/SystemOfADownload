package org.spongepowered.downloads.artifact;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import io.vavr.collection.List;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GetTaggedArtifacts;
import org.spongepowered.downloads.artifact.api.query.GetVersionsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.collection.ArtifactCollectionEntity;
import org.spongepowered.downloads.artifact.collection.TaggedVersionEntity;
import org.spongepowered.downloads.artifact.group.GroupEntity;
import org.taymyr.lagom.javadsl.openapi.AbstractOpenAPIService;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

public class ArtifactServiceImpl extends AbstractOpenAPIService implements ArtifactService {

    private static final Logger LOGGER = LogManager.getLogger(ArtifactServiceImpl.class);
    private final PersistentEntityRegistry registry;

    @Inject
    public ArtifactServiceImpl(final PersistentEntityRegistry registry) {
        this.registry = registry;
        this.registry.register(GroupEntity.class);
        this.registry.register(ArtifactCollectionEntity.class);
        this.registry.register(TaggedVersionEntity.class);
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
    public ServiceCall<GetTaggedArtifacts.Request, GetTaggedArtifacts.Response> getTaggedArtifacts(
        final String groupId,
        final String artifactId
    ) {
        return request -> {
            if (request instanceof GetTaggedArtifacts.MavenVersion) {
                final var mvn = (GetTaggedArtifacts.MavenVersion) request;
                final String mavenCoordinates = groupId + ":" + artifactId;
                final String tagValue = mvn.getTagType() + ":" + mvn.versionPart();
                return this.getTaggedCollection(mavenCoordinates, tagValue)
                    .ask(new TaggedVersionEntity.Command.RequestTaggedVersions(-1, -1));
            } else if (request instanceof GetTaggedArtifacts.SnapshotBuilds) {
                final var snapshot = (GetTaggedArtifacts.SnapshotBuilds) request;
                final String mavenCoordinates = groupId + ":" + artifactId;
                final String tagValue = snapshot.getTagType() + ":" + snapshot.mavenVersion();
                return this.getTaggedCollection(mavenCoordinates, tagValue)
                    .ask(new TaggedVersionEntity.Command.RequestTaggedVersions(-1, -1));
            }
            return CompletableFuture.supplyAsync(() -> new GetTaggedArtifacts.Response.TagUnknown(request.getTagType()));
        };
    }

    @Override
    public ServiceCall<GroupRegistration.RegisterGroupRequest, GroupRegistration.Response> registerGroup() {
        return registration -> {
            final String mavenCoordinates = registration.groupCoordinates();
            final String name = registration.groupName();
            final String website = registration.website();
            return this.getGroupEntity(registration.groupName().toLowerCase(Locale.ROOT))
                .ask(new GroupEntity.GroupCommand.RegisterGroup(mavenCoordinates, name, website));
        };
    }

    @Override
    public ServiceCall<ArtifactRegistration.RegisterCollection, ArtifactRegistration.Response> registerArtifacts(String groupId) {
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
                    if (response instanceof ArtifactRegistration.Response.ArtifactAlreadyRegistered) {
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
            LOGGER.log(Level.INFO, String.format("Requesting group by id: %s", groupId));
            return this.getGroupEntity(groupId.toLowerCase(Locale.ROOT))
                .ask(new GroupEntity.GroupCommand.GetGroup(groupId));
        };
    }

    @Override
    public ServiceCall<NotUsed, NotUsed> registerTaggedVersion(final String groupAndArtifactId, final String pomVersion) {
        return notUsed -> {
            LOGGER.log(Level.DEBUG, String.format("Registering Tagged version: %s with maven artifact %s", pomVersion, groupAndArtifactId));
            final List<String> versions;
            final String tagType = pomVersion.endsWith("-SNAPSHOT") ? "snapshot" : "version";
            if (pomVersion.endsWith("-SNAPSHOT")) {
                versions = List.of(pomVersion);
            } else {
                final String[] split = pomVersion.split("\\.");
                versions = List.of(
                    split[0],
                    split[0] + "." + split[1]
                );
            }
            return versions.map(version ->
                this.getTaggedCollection(groupAndArtifactId, tagType + ":" + version)
                    .ask(new TaggedVersionEntity.Command.RegisterTag(pomVersion))
            )
                .head();
        };
    }

    private PersistentEntityRef<GroupEntity.GroupCommand> getGroupEntity(final String groupId) {
        return this.registry.refFor(GroupEntity.class, groupId);
    }

    private PersistentEntityRef<ArtifactCollectionEntity.Command> getCollection(final String mavenCoordinates) {
        return this.registry.refFor(ArtifactCollectionEntity.class, mavenCoordinates);
    }

    private PersistentEntityRef<TaggedVersionEntity.Command> getTaggedCollection(
        final String mavenGroupAndArtifact, final String tagValue
    ) {
        return this.registry.refFor(TaggedVersionEntity.class, mavenGroupAndArtifact + "_" + tagValue);
    }

}
