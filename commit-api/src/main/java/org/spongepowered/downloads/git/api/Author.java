package org.spongepowered.downloads.git.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;
import java.util.StringJoiner;

@JsonDeserialize
public class Author {

    private final String email;
    private final String name;

    @JsonCreator
    public Author(final String email, final String name) {
        this.email = email;
        this.name = name;
    }

    public String getEmail() {
        return this.email;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final Author author = (Author) o;
        return Objects.equals(this.email, author.email) &&
            Objects.equals(this.name, author.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.email, this.name);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Author.class.getSimpleName() + "[", "]")
            .add("email='" + this.email + "'")
            .add("name='" + this.name + "'")
            .toString();
    }
}
