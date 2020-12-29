package org.spongepowered.downloads.auth.api;

public final class AuthenticationRequest {

    public static final class Request {
        private final String user;
        private final String password;

        public Request(String user, String password) {
            this.user = user;
            this.password = password;
        }

        public String getUser() {
            return user;
        }

        public String getPassword() {
            return password;
        }
    }

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
