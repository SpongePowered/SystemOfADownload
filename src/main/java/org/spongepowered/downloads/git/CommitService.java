package org.spongepowered.downloads.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import org.eclipse.jgit.lib.ObjectId;
import org.spongepowered.downloads.maven.Repository;

public interface CommitService {

    interface CommitException {

    }

    /**
     * Gets the list of {@link Commit}
     * @param repository
     * @param oldSha
     * @param targetSha
     * @return
     */
    Either<CommitException, List<Commit>> getCommitsBetweenShas(
        Repository repository,
        ObjectId oldSha,
        ObjectId targetSha
    );

}
