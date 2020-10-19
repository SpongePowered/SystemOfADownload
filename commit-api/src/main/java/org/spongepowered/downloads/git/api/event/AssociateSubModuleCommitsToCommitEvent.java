package org.spongepowered.downloads.git.api.event;

import org.spongepowered.downloads.git.api.Commit;
import io.vavr.collection.List;

public class AssociateSubModuleCommitsToCommitEvent {

    public final Commit parentCommit;
    public final List<Commit> submoduleCommits;


    public AssociateSubModuleCommitsToCommitEvent(
        final Commit parentCommit, final List<Commit> submoduleCommits
    ) {
        this.parentCommit = parentCommit;
        this.submoduleCommits = submoduleCommits;
    }
}
