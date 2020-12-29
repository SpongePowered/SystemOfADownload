package org.spongepowered.downloads.auth.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.nimbusds.jose.JOSEException;
import org.pac4j.core.authorization.authorizer.Authorizer;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.http.client.direct.HeaderClient;
import org.pac4j.jwt.config.encryption.SecretEncryptionConfiguration;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import org.pac4j.jwt.profile.JwtGenerator;
import org.spongepowered.downloads.auth.api.AuthService;

import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

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

    private final SecretSignatureConfiguration secretSignatureConfiguration;
    private final SecretEncryptionConfiguration secretEncryptionConfiguration;

    private final Authorizer<CommonProfile> adminRoleAuthorizer =
            (webContext, list) -> list.stream().anyMatch(x -> !x.isExpired() && x.getRoles().contains(AuthService.Roles.ADMIN));
    private final Authorizer<CommonProfile> webhookAuthorizer =
            (webContext, list) -> list.stream().anyMatch(x -> !x.isExpired() && x.getRoles().contains(AuthService.Roles.WEBHOOK));

    public AuthModule() {
        final var secureRandom = new SecureRandom();
        final var bytesToGenerate = new byte[256];
        secureRandom.nextBytes(bytesToGenerate);
        this.secretSignatureConfiguration = new SecretSignatureConfiguration(bytesToGenerate);
        secureRandom.nextBytes(bytesToGenerate);
        this.secretEncryptionConfiguration = new SecretEncryptionConfiguration(bytesToGenerate);
    }

    @Override
    protected void configure() {
        this.bind(AuthService.class).to(AuthServiceImpl.class);
    }

    // TODO: If we'd rather use a GraphQL endpoint to log in, rather than
    //  a header, then we don't need this - we do our LDAP query manually and
    //  create a JWT in the body of the request/response method.
    @Provides
    @Named(AuthService.Providers.LDAP)
    protected HeaderClient providerHeaderLDAPClient() {
        final var headerClient = new HeaderClient();
        headerClient.setName("ldap");
        headerClient.setHeaderName(HttpConstants.AUTHENTICATE_HEADER);

        // If we're IP whitelisting it, we just need to check the webcontext. Otherwise we'll want to
        // add tokens and such
        headerClient.setAuthenticator((tokenCredentials, webContext) -> {
            // if failure, not found as it shouldn't even be exposed
            // throw new NotFound("Not found");
            final var profile = new CommonProfile();
            profile.setId("admin");
            profile.addRole(AuthService.Roles.ADMIN);
            profile.setClientName("SOAD Admin User");
            tokenCredentials.setUserProfile(profile);
        });
        headerClient.setAuthorizationGenerator((webContext, commonProfile) -> commonProfile);
        return headerClient;
    }

    // For use with internal endpoints, basically the Sonatype endpoint
    @Provides
    @Named(AuthService.Providers.INTERNAL)
    protected HeaderClient provideInternalAuthenticatorClient() throws ParseException, JOSEException {
        final var headerClient = new HeaderClient();
        headerClient.setName("internal");

        // If we're IP whitelisting it, we just need to check the webcontext. Otherwise we'll want to
        // add tokens and such
        headerClient.setAuthenticator((tokenCredentials, webContext) -> {
            if (webContext != null) {
                // it never is, we're just testing here.
                final var profile = new CommonProfile();
                profile.setId("webhook");
                profile.addRole(AuthService.Roles.WEBHOOK);
                profile.setClientName("SOAD Webhook");
                tokenCredentials.setUserProfile(profile);
                return;
            }
            // if failure, not found as it shouldn't even be exposed
            throw new NotFound("Not found");
        });
        return headerClient;
    }

    // Provides the JWT client - mostly taken from example.
    @Provides
    @Named(AuthService.Providers.JWT)
    protected HeaderClient provideHeaderJwtClient() throws ParseException, JOSEException {
        final var headerClient = new HeaderClient();
        headerClient.setHeaderName(HttpConstants.AUTHORIZATION_HEADER);
        headerClient.setPrefixHeader(HttpConstants.BEARER_HEADER_PREFIX);

        final var jwtAuthenticator = new JwtAuthenticator();
        jwtAuthenticator.addSignatureConfiguration(this.secretSignatureConfiguration);
        jwtAuthenticator.addEncryptionConfiguration(this.secretEncryptionConfiguration);
        headerClient.setAuthenticator(jwtAuthenticator);
        // Custom AuthorizationGenerator to compute the appropriate roles of the authenticated user profile.
        // Roles are fetched from JWT 'roles' attribute.
        // See more http://www.pac4j.org/3.4.x/docs/clients.html#2-compute-roles-and-permissions
        /* headerClient.setAuthorizationGenerator((context, profile) -> {
            if (profile.containsAttribute("roles")) {
                profile.addRoles(profile.getAttribute("roles", Collection.class));
            }
            return profile;
        }); */
        headerClient.setName("jwt_header");
        return headerClient;
    }

    @Provides
    protected JwtGenerator<CommonProfile> provideJwtGenerator() {
        final var generator = new JwtGenerator<>(this.secretSignatureConfiguration, this.secretEncryptionConfiguration);
        generator.setExpirationTime(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        return generator;
    }

    @Provides
    protected Config configProvider(
            final HeaderClient client,
            @Named(AuthService.Providers.LDAP) final HeaderClient ldapClient,
            @Named(AuthService.Providers.INTERNAL) final HeaderClient internalClient) {
        final var config = new Config();
        config.getClients().setDefaultSecurityClients(client.getHeaderName());
        config.getClients().setClients(
                client,
                ldapClient,
                internalClient
        );
        config.addAuthorizer(AuthService.Roles.ADMIN, this.adminRoleAuthorizer);
        config.addAuthorizer(AuthService.Roles.WEBHOOK, this.webhookAuthorizer);
        return config;
    }

}
