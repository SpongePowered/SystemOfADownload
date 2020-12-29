package org.spongepowered.downloads.auth.api;

public final class AuthenticationRequest {

    public static final class Response {
        private final String jwtToken;

        public Response(String jwtToken) {
            this.jwtToken = jwtToken;
        }

        public String getJwtToken() {
            return this.jwtToken;
        }
    }

}
