package org.spongepowered.downloads.artifacts.server.query.transport;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import jakarta.inject.Inject;
import org.spongepowered.downloads.artifact.api.query.GroupResponse;
import org.spongepowered.downloads.artifact.api.query.GroupsResponse;
import org.spongepowered.downloads.artifacts.server.query.group.GroupQueryService;

@Controller("/groups")
public class GroupQueryController {

    @Inject
    private GroupQueryService groupQueryService;

    @Get("/")
    public HttpResponse<GroupsResponse> getGroups() {
        return HttpResponse.ok(groupQueryService.getGroups());
    }

    @Get("/{groupId}")
    public HttpResponse<GroupResponse> getGroupDetails(@PathVariable String groupId) {
        final var groupDetails = groupQueryService.getGroupDetails(groupId);
        return switch (groupDetails) {
            case GroupResponse.Available a -> HttpResponse.ok(a);
            case GroupResponse.Missing n -> HttpResponse.notFound(n);
        };
    }
}
