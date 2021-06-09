package org.spongepowered.downloads.artifact.global;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;

public interface GlobalCommand extends Jsonable {

    final class GetGroups implements GlobalCommand {
        public final ActorRef<GroupsResponse> replyTo;

        public GetGroups(ActorRef<GroupsResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    final class RegisterGroup implements GlobalCommand {
        public final ActorRef<NotUsed> replyTo;
        public final Group group;

        public RegisterGroup(
            final ActorRef<NotUsed> replyTo, final Group group
        ) {
            this.replyTo = replyTo;
            this.group = group;
        }
    }
}
