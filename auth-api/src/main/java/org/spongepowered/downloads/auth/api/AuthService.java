package org.spongepowered.downloads.auth.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

public interface AuthService extends Service {

    final class Providers {

        public static final String LDAP = "ldap";
        public static final String JWT = "jwt";
        public static final String INTERNAL = "internal";
    }

    final class Roles {

        public static final String ADMIN = "soad_admin";
        public static final String WEBHOOK = "soad_webhook";
    }

    // TODO: Proper response, request will probably be this too
    ServiceCall<NotUsed, AuthenticationRequest.Response> login();

    ServiceCall<NotUsed, NotUsed> logout();

    default Descriptor descriptor() {
        return Service.named("auth")
                .withCalls(
                        Service.restCall(Method.POST, "/api/auth/login", this::login),
                        Service.restCall(Method.POST, "/api/auth/logout", this::logout)
                );
    }
}
