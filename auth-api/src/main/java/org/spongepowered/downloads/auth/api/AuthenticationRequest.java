package org.spongepowered.downloads.auth.api;

public final class AuthenticationRequest {

    public record Request(String user, String password) {
    }

    public record Response(String jwtToken) {
    }

}
