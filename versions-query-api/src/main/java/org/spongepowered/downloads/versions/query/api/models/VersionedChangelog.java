package org.spongepowered.downloads.versions.query.api.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.vavr.collection.List;

import java.net.URI;

@JsonDeserialize
public final record VersionedChangelog(
    List<IndexedCommit> commits,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean processing
) {

    @JsonCreator
    public VersionedChangelog {
    }

    @JsonDeserialize
    public final record IndexedCommit(
        VersionedCommit commit,
        List<Submodule> submoduleCommits
    ) {
        @JsonCreator
        public IndexedCommit {
        }
    }

    @JsonDeserialize
    public final record Submodule(
        String name,
        URI gitRepository,
        List<IndexedCommit> commits
    ) {
        @JsonCreator
        public Submodule {
        }
    }

}
