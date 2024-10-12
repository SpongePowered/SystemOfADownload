package org.spongepowered.downloads.artifacts.server.cmd.transport;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import org.spongepowered.downloads.artifact.api.mutation.Update;
import org.spongepowered.downloads.artifact.api.query.GroupRegistration;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupCommand;
import org.spongepowered.downloads.artifacts.server.cmd.group.GroupService;

@Controller("/groups")
@Requires("command")
public class GroupCommandController {

    @Inject
    private GroupService groupService;

    @Post
    @Transactional
    public HttpResponse<GroupRegistration.Response> registerGroup(@Body GroupCommand.RegisterGroup groupDTO) {
        return groupService.registerGroup(groupDTO);
    }

    @Patch("/{groupId}")
    @Transactional
    public HttpResponse<GroupResponse> updateGroup(final String groupId, @Body @Valid Update groupUpdateDTO) {
        return groupService.updateGroup(groupId, groupUpdateDTO);
    }

}
