/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads.git.utils;

import io.vavr.collection.List;
import io.vavr.collection.TreeMultimap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.submodule.SubmoduleWalk;
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
import java.util.stream.Collectors;

public class JgitCommitToApiCommit {

    public static List<Commit> getCommitsFromRepo(
        final CommitCommand.GetCommitsBetween commitsRequest, final Repository repo
    ) throws IOException, GitAPIException {
        final Path productRepo = Files.createTempDirectory("soad-commit-service").toAbsolutePath();
        final Git gitRepo = Git.cloneRepository()
            .setCloneAllBranches(true)
            .setCloneSubmodules(true)
            .setDirectory(productRepo.toFile())
            .setURI(repo.getRepoUrl())
            .call();

        final var fromCommit = ObjectId.fromString(commitsRequest.diff().fromSha().toHexString());
        final var toCommit = ObjectId.fromString(commitsRequest.diff().toSha().toHexString());
        final var logCommits = gitRepo.log().addRange(fromCommit, toCommit).call();
        // TODO: Submodules
        var commits = List.<Commit>empty();
        for (final var commit : logCommits) {
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
