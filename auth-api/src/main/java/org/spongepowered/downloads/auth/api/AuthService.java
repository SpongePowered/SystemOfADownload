package org.spongepowered.downloads.auth.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.taymyr.lagom.javadsl.openapi.OpenAPIService;
import org.taymyr.lagom.javadsl.openapi.OpenAPIUtils;

public interface AuthService extends OpenAPIService {

    final class Providers {

        public static final String LDAP = "ldap";
    }

    // The response will contain a JWT if the authentication succeeded.
    // Uses standard Basic auth over HTTPS to login.
    ServiceCall<NotUsed, AuthenticationRequest.Response> login();

    ServiceCall<NotUsed, NotUsed> logout();

    default Descriptor descriptor() {
        return OpenAPIUtils.withOpenAPI(Service.named("auth")
                .withCalls(
                        Service.restCall(Method.POST, "/api/auth/login", this::login),
                        Service.restCall(Method.POST, "/api/auth/logout", this::logout)
                )
            .withAutoAcl(true)
        );
    }
}
