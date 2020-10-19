package org.spongepowered.downloads.git.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

@JsonDeserialize
@Immutable
public final class Repository {

    private final UUID id;
    private final String name;
    private final String repoUrl;
    private final @Nullable String website;

    public Repository(
        final UUID id, final String name, final String repoUrl, @Nullable final String website
    ) {
        this.id = id;
        this.name = name;
        this.repoUrl = repoUrl;
        this.website = website;
    }

    public UUID getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getRepoUrl() {
        return this.repoUrl;
    }

    public Optional<String> getWebsite() {
        return Optional.ofNullable(this.website);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final Repository that = (Repository) o;
        return this.id.equals(that.id) &&
            this.name.equals(that.name) &&
            this.repoUrl.equals(that.repoUrl) &&
            Objects.equals(this.website, that.website);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.name, this.repoUrl, this.website);
    }

    @Override
    public String toString() {
        return new StringJoiner(
            ", ", Repository.class.getSimpleName() + "[", "]")
            .add("id=" + this.id)
            .add("name='" + this.name + "'")
            .add("repoUrl='" + this.repoUrl + "'")
            .add("website='" + this.website + "'")
            .toString();
    }
}
