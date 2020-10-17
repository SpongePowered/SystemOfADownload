package org.spongepowered.downloads.git;

import io.vavr.collection.List;

import java.time.ZonedDateTime;
import java.util.UUID;

public final record Commit(
    long shardId1,
    long shardId2,
    int shardId3,
    UUID repo,
    String header,
    String messageBody,
    String author,
    ZonedDateTime commitDate,
    List<SubmoduleCommit> subCommits
) {

}
