package org.spongepowered.downloads.artifacts;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;
import org.spongepowered.downloads.artifacts.transport.GetArtifactDetailsResponse;
import org.spongepowered.downloads.artifacts.transport.GetArtifactsResponse;
import org.spongepowered.downloads.artifacts.transport.GroupResponse;
import org.spongepowered.downloads.artifacts.transport.GroupsResponse;

class ArtifactQueries extends AbstractBehavior<ArtifactQueries.Command> {

    public ArtifactQueries(final ActorContext<Command> context) {
        super(context);
    }

    sealed interface Command {

        record GetGroup(
            String groupId,
            ActorRef<GroupResponse> replyTo
        ) implements Command {
        }

        record GetArtifacts(
            String groupId,
            ActorRef<GetArtifactsResponse> replyTo
        ) implements Command {
        }

        record GetGroups(
            ActorRef<GroupsResponse> replyTo
        ) implements Command {
        }

        record GetArtifactDetails(
            String groupId,
            String artifactId,
            ActorRef<GetArtifactDetailsResponse> replyTo
        ) implements Command {
        }

    }


    @Override
    public Receive<Command> createReceive() {
        return this.newReceiveBuilder()
            .build();
    }
}
