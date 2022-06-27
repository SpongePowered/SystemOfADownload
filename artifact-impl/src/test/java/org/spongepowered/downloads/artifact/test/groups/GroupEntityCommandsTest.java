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
package org.spongepowered.downloads.artifact.test.groups;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import io.vavr.collection.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.group.GroupCommand;
import org.spongepowered.downloads.artifact.group.GroupEntity;
import org.spongepowered.downloads.artifact.group.GroupEvent;
import org.spongepowered.downloads.artifact.group.state.GroupState;
import org.spongepowered.downloads.artifact.test.akka.EventBehaviorTestkit;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class GroupEntityCommandsTest implements BeforeEachCallback {

    // If actor refs are being used, the testkit needs to have the akka modules
    // locally.
    public static final TestKitJunitResource testkit = EventBehaviorTestkit.createTestKit();

    private final EventSourcedBehaviorTestKit<GroupCommand, GroupEvent, GroupState> behaviorKit = EventSourcedBehaviorTestKit.create(
        testkit.system(), GroupEntity.create(
            new EntityContext<>(
                EntityTypeKey.create(GroupCommand.class, "GroupEntity"),
                "org.spongepowered",
                null
            )));

    @Override
    public void beforeEach(final ExtensionContext context) {
        this.behaviorKit.clear();
    }

    @Test
    public void verifyGroupRegistration() {
        final var unhandledMessageProbe = testkit.createUnhandledMessageProbe();
        final var spongePowered = new Group("org.spongepowered", "SpongePowered", "https://spongepowered.org/");
        final var result = behaviorKit.<GroupRegistration.Response>runCommand(
            replyTo -> new GroupCommand.RegisterGroup(
                spongePowered.groupCoordinates(), spongePowered.name(), spongePowered.website(), replyTo));
        Assertions.assertEquals(new GroupRegistration.Response.GroupRegistered(spongePowered), result.reply());
        unhandledMessageProbe.expectNoMessage();
    }

    @Test
    public void verify404GroupAvailable() {
        final var unhandledMessageProbe = testkit.createUnhandledMessageProbe();
        final var spongePowered = new Group("org.spongepowered", "SpongePowered", "https://spongepowered.org/");
        final var result = behaviorKit.<GroupResponse>runCommand(
            replyTo -> new GroupCommand.GetGroup(spongePowered.groupCoordinates(), replyTo));
        Assertions.assertEquals(new GroupResponse.Missing("org.spongepowered"), result.reply());
        unhandledMessageProbe.expectNoMessage();
    }

    private Group setUpGroup() {
        final var spongePowered = new Group("org.spongepowered", "SpongePowered", "https://spongepowered.org/");
        behaviorKit.<GroupRegistration.Response>runCommand(
            replyTo -> new GroupCommand.RegisterGroup(
                spongePowered.groupCoordinates(), spongePowered.name(), spongePowered.website(), replyTo));
        return spongePowered;
    }

    @Test
    public void verify200GroupAvailable() {
        final var unhandledMessageProbe = testkit.createUnhandledMessageProbe();
        final var spongePowered = this.setUpGroup();
        final var result = behaviorKit.<GroupResponse>runCommand(
            replyTo -> new GroupCommand.GetGroup(spongePowered.groupCoordinates(), replyTo));
        Assertions.assertEquals(new GroupResponse.Available(spongePowered), result.reply());
        unhandledMessageProbe.expectNoMessage();
    }

    @Test
    public void verify200ArtifactsAvailable() {
        final var unhandledMessageProbe = testkit.createUnhandledMessageProbe();
        final var spongePowered = this.setUpGroup();
        final var result = behaviorKit.<GetArtifactsResponse>runCommand(
            replyTo -> new GroupCommand.GetArtifacts(spongePowered.groupCoordinates(), replyTo));
        Assertions.assertEquals(new GetArtifactsResponse.ArtifactsAvailable(List.empty()), result.reply());
        unhandledMessageProbe.expectNoMessage();
    }

    @Test
    public void verify404ArtifactsUnavailable() {
        final var unhandledMessageProbe = testkit.createUnhandledMessageProbe();
        final var spongePowered = new Group("org.spongepowered", "SpongePowered", "https://spongepowered.org/");
        final var result = behaviorKit.<GetArtifactsResponse>runCommand(
            replyTo -> new GroupCommand.GetArtifacts(spongePowered.groupCoordinates(), replyTo));
        Assertions.assertEquals(new GetArtifactsResponse.GroupMissing("org.spongepowered"), result.reply());
        unhandledMessageProbe.expectNoMessage();
    }

}
