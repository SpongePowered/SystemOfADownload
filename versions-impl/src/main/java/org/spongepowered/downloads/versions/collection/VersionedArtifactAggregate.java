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
package org.spongepowered.downloads.versions.collection;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.GetVersionResponse;
import org.spongepowered.downloads.versions.api.models.GetVersionsResponse;
import org.spongepowered.downloads.versions.api.models.TagRegistration;
import org.spongepowered.downloads.versions.api.models.VersionRegistration;
import org.spongepowered.downloads.versions.api.models.tags.ArtifactTagValue;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class VersionedArtifactAggregate
    extends EventSourcedBehaviorWithEnforcedReplies<ACCommand, ACEvent, ACState> {

    public static EntityTypeKey<ACCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(ACCommand.class, "VersionedArtifact");
    private final Function<ACEvent, Set<String>> tagger;

    public static VersionedArtifactAggregate create(final EntityContext<ACCommand> context) {
        return new VersionedArtifactAggregate(context);
    }

    private VersionedArtifactAggregate(final EntityContext<ACCommand> context) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        this.tagger = AkkaTaggerAdapter.fromLagom(context, ACEvent.INSTANCE);

    }

    @Override
    public ACState emptyState() {
        return ACState.empty();
    }

    @Override
    public EventHandler<ACState, ACEvent> eventHandler() {
        final var builder = this.newEventHandlerBuilder();
        builder.forAnyState()
            .onEvent(ACEvent.ArtifactCoordinatesUpdated.class, this::updateCoordinates)
            .onEvent(ACEvent.ArtifactVersionRegistered.class, this::updateVersionRegistered);
        builder.forState(ACState::isRegistered)
            .onEvent(ACEvent.ArtifactTagRegistered.class, this::updateArtifactTag)
        ;
        return builder.build();
    }

    @Override
    public Set<String> tagsFor(final ACEvent acEvent) {
        return this.tagger.apply(acEvent);
    }

    @Override
    public CommandHandlerWithReply<ACCommand, ACEvent, ACState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder();
        builder.forState(ACState::isRegistered)
            .onCommand(ACCommand.RegisterArtifact.class, (cmd) -> this.Effect().reply(cmd.replyTo, NotUsed.notUsed()))
            .onCommand(ACCommand.GetSpecificVersion.class, (state, cmd) -> state.collection().get(cmd.version())
                .map(tag -> new GetVersionResponse.VersionInfo(new ArtifactCollection(HashMap.empty(), tag.coordinates())))
                .map(versionInfo -> this.Effect().reply(cmd.replyTo(), versionInfo))
                .getOrElse(this.Effect().reply(cmd.replyTo(), new GetVersionResponse.VersionMissing(cmd.version()))))
            .onCommand(ACCommand.RegisterArtifactTag.class, this::handlRegisterTag)
        ;
        builder.forAnyState()
            .onCommand(ACCommand.RegisterVersion.class, this::handleRegisterVersion)
            .onCommand(ACCommand.RegisterArtifact.class, this::handleRegisterArtifact)
            .onCommand(ACCommand.GetVersions.class, this::respondToGetVersions);
        return builder.build();
    }

    private ReplyEffect<ACEvent, ACState> handleRegisterVersion(
        final ACState state, final ACCommand.RegisterVersion cmd
    ) {
        if (state.collection().containsKey(cmd.coordinates().version)) {
            return this.Effect().reply(
                cmd.replyTo(),
                new VersionRegistration.Response.ArtifactAlreadyRegistered(cmd.coordinates().asArtifactCoordinates())
            );
        }
        return this.Effect()
            .persist(new ACEvent.ArtifactVersionRegistered(cmd.coordinates()))
            .thenReply(cmd.replyTo(), (s) -> new VersionRegistration.Response.RegisteredArtifact(cmd.coordinates()));
    }

    private ReplyEffect<ACEvent, ACState> handleRegisterArtifact(
        final ACState state,
        final ACCommand.RegisterArtifact cmd
    ) {
        return this.Effect()
            .persist(new ACEvent.ArtifactCoordinatesUpdated(cmd.coordinates))
            .thenReply(cmd.replyTo, (s) -> NotUsed.notUsed());
    }

    private ReplyEffect<ACEvent, ACState> handlRegisterTag(
        final ACState state,
        final ACCommand.RegisterArtifactTag cmd
    ) {
        if (state.tags().containsKey(cmd.entry().name().toLowerCase(Locale.ROOT))) {
            return this.Effect().reply(cmd.replyTo(), new TagRegistration.Response.TagAlreadyRegistered(cmd.entry().name()));
        }
        return this.Effect()
            .persist(new ACEvent.ArtifactTagRegistered(cmd.entry()))
            .thenReply(cmd.replyTo(), (s) -> new TagRegistration.Response.TagSuccessfullyRegistered());
    }


    private ACState updateCoordinates(
        final ACState state, final ACEvent.ArtifactCoordinatesUpdated event
    ) {
        return new ACState(event.coordinates, state.collection(), false, HashMap.empty());
    }

    private ACState updateVersionRegistered(
        final ACState state, final ACEvent.ArtifactVersionRegistered event
    ) {
        final Map<String, ArtifactTagValue> newMap = state
            .collection()
            .computeIfAbsent(event.version.version, (version) -> {

                final Map<String, String> tagValues = state.tags().mapValues(tag -> {
                    final var expectedGroup = tag.matchingGroup();
                    final var matcher = Pattern.compile(tag.regex()).matcher(version);
                    if (matcher.find()) {
                        return Try.of(() -> matcher.group(expectedGroup))
                            .getOrElse("");
                    }
                    return "";
                });
                return new ArtifactTagValue(state.coordinates().version(version), tagValues);
            })
            ._2;
        final var sorted = newMap
            .toSortedMap(Comparator.comparing(ComparableVersion::new).reversed(), (key) -> key._1, (tuple) -> tuple._2);
        return new ACState(
            event.version.asArtifactCoordinates(),
            sorted,
            false,
            HashMap.empty()
        );
    }

    private ACState updateArtifactTag(
        final ACState state,
        final ACEvent.ArtifactTagRegistered event
    ) {
        final var tagMap = state.tags().put(event.entry().name().toLowerCase(Locale.ROOT), event.entry());
        final Map<String, ArtifactTagValue> newTagValues = state.collection().keySet()
            .toMap(version -> version, version -> {
                final Map<String, String> tagValues = tagMap.mapValues(tag -> {
                    final var expectedGroup = tag.matchingGroup();
                    final var matcher = Pattern.compile(tag.regex()).matcher(version);
                    if (matcher.find()) {
                        return Try.of(() -> matcher.group(expectedGroup))
                            .getOrElse("");
                    }
                    return "";
                });
                return new ArtifactTagValue(state.coordinates().version(version), tagValues);
            })
            .toSortedMap(Comparator.comparing(ComparableVersion::new).reversed(), (key) -> key._1, (tuple) -> tuple._2);
        return new ACState(state.coordinates(), newTagValues, false, tagMap);
    }

    private ReplyEffect<ACEvent, ACState> respondToGetVersions(
        final ACState state,
        final ACCommand.GetVersions cmd
    ) {
        if (!state.coordinates().groupId.equalsIgnoreCase(cmd.groupId())) {
            return this.Effect().reply(cmd.replyTo(), new GetVersionsResponse.GroupUnknown(cmd.groupId()));
        }
        if (!state.coordinates().artifactId.equalsIgnoreCase(cmd.artifactId())) {
            return this.Effect().reply(cmd.replyTo(), new GetVersionsResponse.ArtifactUnknown(cmd.artifactId()));
        }

        final var discoveredTags = cmd.tags().map(raw -> raw.split(",")).map(List::of).orElse(List.of())
            .map(tagVal -> tagVal.split(":"))
            .filter(array -> array.length == 2)
            .toMap(array -> array[0].toLowerCase(Locale.ROOT), array -> array[1]);
        final Predicate<ArtifactTagValue> artifactLevelFilter = (tagValue) -> tagValue.tagValues()
            .filterKeys(discoveredTags::containsKey)
            .containsAll(discoveredTags);
        final var rawMap = state.collection().filterValues(artifactLevelFilter);
        final var offsetedMap = cmd.offset().filter(i -> i > 0)
            .map(rawMap::drop)
            .orElse(rawMap);
        final var limitedMap = cmd.limit().filter(i -> i > 0)
            .map(offsetedMap::take)
            .orElse(offsetedMap);
        return this.Effect().reply(cmd.replyTo(), new GetVersionsResponse.VersionsAvailable(limitedMap));
    }

}
