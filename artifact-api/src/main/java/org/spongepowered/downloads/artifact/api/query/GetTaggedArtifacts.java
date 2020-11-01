package org.spongepowered.downloads.artifact.api.query;

import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;

public final class GetTaggedArtifacts {

    public sealed interface Request {
        Type getType();
        String getTagType();
    }

    public enum Type { VERSION, SNAPSHOT; }

    public final record MavenVersion(String versionPart) implements Request {
        @Override
        public String getTagType() {
            return Type.VERSION.name();
        }

        @Override
        public Type getType() {
            return Type.VERSION;
        }
    }
    public final record SnapshotBuilds(String mavenVersion) implements Request {
        @Override
        public String getTagType() {
            return Type.SNAPSHOT.name();
        }

        @Override
        public Type getType() {
            return Type.SNAPSHOT;
        }
    }

    public sealed interface Response {

        final record TagUnknown(String tag) implements Response {
        }
    }
    public final record VersionsAvailable(Map<String, ArtifactCollection> artifacts) implements Response {}
    public final record GroupUnknown(String groupId) implements Response {}
    public final record ArtifactUnknown(String artifactId) implements Response {}
}
