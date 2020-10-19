package org.spongepowered.downloads.git;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.collection.SortedMultimap;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.Repository;

import java.util.UUID;

public class CommitsState implements Jsonable {

    public final Map<Repository, SortedMultimap<String, Commit>> repositoryCommits;

    public CommitsState(
        final Map<Repository, SortedMultimap<String, Commit>> repositoryCommits
    ) {
        this.repositoryCommits = repositoryCommits;
    }
}
