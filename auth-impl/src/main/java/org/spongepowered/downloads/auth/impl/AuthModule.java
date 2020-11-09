package org.spongepowered.downloads.auth.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.lightbend.lagom.javadsl.api.transport.Forbidden;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.nimbusds.jose.JOSEException;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.http.client.direct.HeaderClient;
import org.pac4j.lagom.jwt.JwtAuthenticatorHelper;
import org.spongepowered.downloads.auth.api.AuthService;

import java.text.ParseException;
import java.util.Collection;

// See: https://github.com/pac4j/lagom-pac4j-java-demo
//
// We could alternatively go for HTTP or Cookie auth that uses LDAP as a backing store, but
// JWT may suffice - we can then just use LDAP as a way enable JWT generation. We can also just
// require LDAP every time... but that's probably not what we want.
//
// JWT is great as we won't want to store sessions. However, it's not great in the sense that it'll
// be hard to expire them when done with - but we could just generate secrets on the fly when necessary
// which expire themselves after some time, and can be forcefully expired when needed.
//
// JWTs should be short lived anyway.
public final class AuthModule extends AbstractModule {

    public static final String LDAP_PROVIDER = "ldap";
    private static final String INTERNAL = "internal";

    @Override
    protected void configure() {
        this.bind(AuthService.class).to(AuthServiceImpl.class);
    }

    // TODO: If we'd rather use a GraphQL endpoint to log in, rather than
    // a header, then we don't need this - we do our LDAP query manually and
    // create a JWT in the body of the request/response method.
    @Provides
    @Named(AuthModule.LDAP_PROVIDER)
    protected HeaderClient providerHeaderLDAPClient() {
        return new HeaderClient(); // there'll be a different client we should use.
    }

    // For use with internal endpoints, basically the Sonatype endpoint
    @Provides
    @Named(AuthModule.INTERNAL)
    protected HeaderClient provideInternalAuthorizerClient() throws ParseException, JOSEException {
        final var headerClient = new HeaderClient();
        headerClient.setName("internal");

        // If we're IP whitelisting it, we just need to check the webcontext. Otherwise we'll want to
        // add tokens and such
        headerClient.setAuthenticator((tokenCredentials, webContext) -> {
            // if failure, not found as it shouldn't even be exposed
            throw new NotFound("Not found");
        });
        return headerClient;
    }

    // Provides the JWT client - mostly taken from example.
    @Provides
    @SuppressWarnings("unchecked")
    protected HeaderClient provideHeaderJwtClient(com.typesafe.config.Config configuration) throws ParseException, JOSEException {
        final var headerClient = new HeaderClient();
        headerClient.setHeaderName(HttpConstants.AUTHORIZATION_HEADER);
        headerClient.setPrefixHeader(HttpConstants.BEARER_HEADER_PREFIX);
        // Strongly recommendation use `JwtAuthenticatorHelper` for initializing `JwtAuthenticator`.
        // TODO: Make this SOAD specific
        headerClient.setAuthenticator(JwtAuthenticatorHelper.parse(configuration.getConfig("pac4j.lagom.jwt.authenticator")));
        // Custom AuthorizationGenerator to compute the appropriate roles of the authenticated user profile.
        // Roles are fetched from JWT 'roles' attribute.
        // See more http://www.pac4j.org/3.4.x/docs/clients.html#2-compute-roles-and-permissions
        headerClient.setAuthorizationGenerator((context, profile) -> {
            if (profile.containsAttribute("roles")) {
                profile.addRoles(profile.getAttribute("roles", Collection.class));
            }
            return profile;
        });
        headerClient.setName("jwt_header");
        return headerClient;
    }

    @Provides
    protected Config configProvider(
            final HeaderClient client,
            @Named(AuthModule.LDAP_PROVIDER) final HeaderClient ldapClient,
            @Named(AuthModule.INTERNAL) final HeaderClient internalClient) {
        final var config = new Config();
        config.getClients().setDefaultSecurityClients(client.getHeaderName());
        config.getClients().setClients(
                client,
                ldapClient,
                internalClient
        );
        return config;
    }

}
