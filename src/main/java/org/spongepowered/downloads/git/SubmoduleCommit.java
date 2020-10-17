package org.spongepowered.downloads.git;

public record SubmoduleCommit(
    Commit parent,
    Commit child
) {

}
