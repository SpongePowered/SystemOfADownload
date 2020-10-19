package org.spongepowered.downloads.git.api;

import javax.annotation.concurrent.Immutable;

/**
 * A Git commit is 160-bit id, which means that it can be composed of
 * two {@code long}s and an {@code int}. In totality, a commit can always
 * have the same sha as another commit for a totally different repository,
 * however, it's impossible for git to generate the same commit sha within
 * the same commit history (since a SHA-1 collision on a diff will be
 * rendered as the "same commit").
 * <p>To borrow an excerpt from the <a href="https://git-scm.com/book/en/v2/Git-Tools-Revision-Selection">Git book</a>:
 * <blockquote>
 * Here’s an example to give you an idea of what it would take to get a
 * SHA-1 collision. If all 6.5 billion humans on Earth were programming,
 * and every second, each one was producing code that was the equivalent
 * of the entire Linux kernel history (3.6 million Git objects) and pushing
 * it into one enormous Git repository, it would take roughly 2 years until
 * that repository contained enough objects to have a 50% probability of
 * a single SHA-1 object collision.
 * </blockquote>
 */
@Immutable
public final class CommitSha {

    public final long shaPart1, shaPart2;
    public final int shaPart3;

    public CommitSha(final long shaPart1, final long shaPart2, final int shaPart3) {
        this.shaPart1 = shaPart1;
        this.shaPart2 = shaPart2;
        this.shaPart3 = shaPart3;
    }

}
