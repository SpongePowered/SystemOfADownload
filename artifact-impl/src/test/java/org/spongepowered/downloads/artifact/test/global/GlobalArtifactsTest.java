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
package org.spongepowered.downloads.artifact.test.global;

import akka.Done;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.typed.PersistenceId;
import com.typesafe.config.ConfigFactory;
import io.vavr.collection.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.global.GlobalCommand;
import org.spongepowered.downloads.artifact.global.GlobalEvent;
import org.spongepowered.downloads.artifact.global.GlobalRegistration;
import org.spongepowered.downloads.artifact.global.GlobalState;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GlobalArtifactsTest implements BeforeEachCallback {

    // If actor refs are being used, the testkit needs to have the akka modules
    // locally.
    public static final TestKitJunitResource testkit = new TestKitJunitResource(ConfigFactory.parseString(
            """
            akka.serialization.jackson {
                # The Jackson JSON serializer will register these modules.
                jackson-modules += "akka.serialization.jackson.AkkaJacksonModule"
                jackson-modules += "akka.serialization.jackson.AkkaTypedJacksonModule"
                # AkkaStreamsModule optionally included if akka-streams is in classpath
                jackson-modules += "akka.serialization.jackson.AkkaStreamJacksonModule"
                jackson-modules += "com.fasterxml.jackson.module.paramnames.ParameterNamesModule"
                jackson-modules += "com.fasterxml.jackson.datatype.jdk8.Jdk8Module"
                jackson-modules += "com.fasterxml.jackson.module.scala.DefaultScalaModule"
                jackson-modules += "io.vavr.jackson.datatype.VavrModule"
            }
            """
        )
        .resolve() // Resolve the config first
        .withFallback(EventSourcedBehaviorTestKit.config()));

    private final EventSourcedBehaviorTestKit<GlobalCommand, GlobalEvent, GlobalState> behaviorKit = EventSourcedBehaviorTestKit.create(
        testkit.system(), GlobalRegistration.create(
            "org.spongepowered",
            PersistenceId.of("GlobalEntity", "org.spongepowered")
        ));


    @Override
    public void beforeEach(final ExtensionContext context) {
        this.behaviorKit.clear();
    }

    @Test
    public void verifyGlobalRegistration() {
        final var unhandledMessageProbe = testkit.createUnhandledMessageProbe();
        final var spongePowered = new Group("org.spongepowered", "SpongePowered", "https://spongepowered.org/");
        final var result = behaviorKit.<Done>runCommand(
            replyTo -> new GlobalCommand.RegisterGroup(replyTo, spongePowered));
        Assertions.assertEquals(Done.done(), result.reply());
        Assertions.assertEquals(new GlobalState(List.of(spongePowered)), result.state());
        unhandledMessageProbe.expectNoMessage();
    }

    @Test
    public void validateDoubleRegistration() {
        final var unhandledMessageProbe = testkit.createUnhandledMessageProbe();
        final var spongePowered = new Group("org.spongepowered", "SpongePowered", "https://spongepowered.org/");
        final var result = behaviorKit.<Done>runCommand(
            replyTo -> new GlobalCommand.RegisterGroup(replyTo, spongePowered));
        Assertions.assertEquals(Done.done(), result.reply());
        Assertions.assertEquals(new GlobalState(List.of(spongePowered)), result.state());
        unhandledMessageProbe.expectNoMessage();
        final var duplicatedRegistration = behaviorKit.<Done>runCommand(
            replyTo -> new GlobalCommand.RegisterGroup(replyTo, spongePowered));
        Assertions.assertEquals(Done.done(), duplicatedRegistration.reply());
        Assertions.assertTrue(duplicatedRegistration.hasNoEvents());
        Assertions.assertEquals(new GlobalState(List.of(spongePowered)), duplicatedRegistration.state());
    }

}
