package org.spongepowered.downloads.git.api;

import javax.annotation.concurrent.Immutable;

@Immutable
public record CommitDiff(
    CommitSha fromSha,
    CommitSha toSha
) {
}
