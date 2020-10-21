package org.spongepowered.downloads.git.utils;

import io.vavr.collection.List;
import io.vavr.collection.TreeMultimap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.spongepowered.downloads.git.CommitCommand;
import org.spongepowered.downloads.git.api.Author;
import org.spongepowered.downloads.git.api.Commit;
import org.spongepowered.downloads.git.api.CommitSha;
import org.spongepowered.downloads.git.api.Repository;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class JgitCommitToApiCommit {

    public static List<Commit> getCommitsFromRepo(
        final CommitCommand.GetCommitsBetween commitsRequest, final Repository repo
    ) throws IOException, GitAPIException {
        final Path productRepo = Files.createTempDirectory("soad-commit-service").toAbsolutePath();
        final Git gitRepo = Git.cloneRepository()
            .setCloneAllBranches(true)
            .setDirectory(productRepo.toFile())
            .setURI(repo.getRepoUrl())
            .call();

        final var fromCommit = ObjectId.fromString(commitsRequest.diff.fromSha.toHexString());
        final var toCommit = ObjectId.fromString(commitsRequest.diff.toSha.toHexString());
        final var logCommits = gitRepo.log().addRange(fromCommit, toCommit).call();
        // TODO: Submodules
        var commits = List.<Commit>empty();
        for (final var commit : logCommits) {
            final ObjectId id = commit.getId();
            // Because jgit can't just give us the flipping 5 ints.....
            final var bytes = new byte[Constants.OBJECT_ID_LENGTH];
            id.copyRawTo(bytes, 0);
            commits = commits.append(toCommit(repo, commit));
        }
        return commits;
    }

    public static Commit toCommit(final Repository repository, final RevCommit jcommit) {
        // Seriously... why can't they just give us the damn 5 ints....
        final var bytes = new byte[Constants.OBJECT_ID_LENGTH];
        jcommit.getId().copyTo(bytes, 0);

        final IntBuffer intBuf =
            ByteBuffer.wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .asIntBuffer();
        final int[] array = new int[intBuf.remaining()];
        intBuf.get(array);
        final long shaPart1 = array[0] << 16 & array[1];
        final long shaPart2 = array[2] << 16 & array[3];
        final var commitSha = new CommitSha(shaPart1, shaPart2, array[4]);
        final PersonIdent jAuthor = jcommit.getAuthorIdent();
        final Author author = new Author(jAuthor.getEmailAddress(), jAuthor.getName());
        return new Commit(commitSha, author, repository, TreeMultimap.withSeq().empty());
    }
}
