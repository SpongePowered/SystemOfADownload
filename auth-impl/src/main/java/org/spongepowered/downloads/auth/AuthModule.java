package org.spongepowered.downloads.auth;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.nimbusds.jose.JOSEException;
import org.ldaptive.ConnectionConfig;
import org.ldaptive.DefaultConnectionFactory;
import org.ldaptive.auth.PooledBindAuthenticationHandler;
import org.ldaptive.auth.SearchDnResolver;
import org.ldaptive.pool.BlockingConnectionPool;
import org.ldaptive.pool.IdlePruneStrategy;
import org.ldaptive.pool.PoolConfig;
import org.ldaptive.pool.PooledConnectionFactory;
import org.ldaptive.pool.SearchValidator;
import org.pac4j.core.authorization.authorizer.Authorizer;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.http.client.direct.DirectBasicAuthClient;
import org.pac4j.http.client.direct.HeaderClient;
import org.pac4j.http.client.direct.IpClient;
import org.pac4j.http.credentials.authenticator.IpRegexpAuthenticator;
import org.pac4j.jwt.config.encryption.SecretEncryptionConfiguration;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import org.pac4j.jwt.profile.JwtGenerator;
import org.pac4j.ldap.profile.service.LdapProfileService;
import org.spongepowered.downloads.auth.api.AuthService;
import org.spongepowered.downloads.auth.api.SOADAuth;

import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

// See: https://github.com/pac4j/lagom-pac4j-java-demo
//
// We could alternatively go for Cookie auth that uses LDAP as an initial check, but
// JWT may suffice - we can then just use LDAP as a way enable JWT generation. We can also just
// require LDAP every time... but that's probably not what we want.
//
// JWT is great as we won't want to store sessions. However, it's not great in the sense that it'll
// be hard to expire them when done with - but we could just generate secrets on the fly when necessary
// which expire themselves after some time, and can be forcefully expired when needed.
//
// JWTs should be short lived anyway.
public final class AuthModule extends AbstractModule implements ServiceGuiceSupport {

    private final String ipAddressWhitelistRegex = Objects.requireNonNullElse(System.getenv("IP_WHITELIST"), "(127\\.0\\.0\\.1|localhost)");
    private final boolean useDummyCredentials = Objects.requireNonNullElse(System.getenv("USE_DUMMY_LDAP"), "false").equalsIgnoreCase("true");
    private final String ldapUrl = Objects.requireNonNullElse(System.getenv("LDAP_URL"), "ldap://localhost:389");
    private final String ldapBaseUserDn = Objects.requireNonNullElse(System.getenv("LDAP_BASE_USER_DN"), "dc=spongepowered,dc=org");
    private final String ldapSoadOu = Objects.requireNonNullElse(System.getenv("LDAP_SOAD_OU"), "soad");

    private final SecretSignatureConfiguration secretSignatureConfiguration;
    private final SecretEncryptionConfiguration secretEncryptionConfiguration;

    private final Authorizer<CommonProfile> adminRoleAuthorizer =
            (webContext, list) -> list.stream().anyMatch(x -> !x.isExpired() && x.getRoles().contains(AuthService.Roles.ADMIN));
    private final Authorizer<CommonProfile> webhookAuthorizer =
            (webContext, list) -> list.stream().anyMatch(x -> !x.isExpired() && x.getRoles().contains(AuthService.Roles.WEBHOOK));

    public AuthModule() {
        final var secureRandom = new SecureRandom();
        final var bytesToGenerate = new byte[32];
        secureRandom.nextBytes(bytesToGenerate);
        this.secretSignatureConfiguration = new SecretSignatureConfiguration(bytesToGenerate);
        secureRandom.nextBytes(bytesToGenerate);
        this.secretEncryptionConfiguration = null;
    }

    @Override
    protected void configure() {
        this.bindService(AuthService.class, AuthServiceImpl.class);
    }

    // TODO: If we'd rather use a GraphQL endpoint to log in, rather than
    //  a header, then we don't need this - we do our LDAP query manually and
    //  create a JWT in the body of the request/response method.
    @Provides
    @Named(AuthService.Providers.LDAP)
    protected DirectClient<UsernamePasswordCredentials, CommonProfile> providerHeaderLDAPClient() {
        final var basicAuthClient = new DirectBasicAuthClient();
        basicAuthClient.setName(AuthService.Providers.LDAP);

        final Authenticator<UsernamePasswordCredentials> authenticator;
        // TODO: Ditch this once we're running.
        if (this.useDummyCredentials) {
            authenticator = ((usernamePasswordCredentials, webContext) -> {
                if (usernamePasswordCredentials.getUsername().equals("soad") && usernamePasswordCredentials.getPassword().equals("systemofadownload")) {
                    final var profile = new CommonProfile();
                    profile.setId("soad");
                    usernamePasswordCredentials.setUserProfile(profile);
                    return;
                }
                throw new CredentialsException("Incorrect username and password.");
            });
        } else {
            // http://www.pac4j.org/3.4.x/docs/authenticators/ldap.html
            // This could probably be improved... but the documentation leaves something to be desired
            final var dnResolver = new SearchDnResolver();
            dnResolver.setBaseDn(this.ldapBaseUserDn);
            dnResolver.setUserFilter("(&(ou=" + this.ldapSoadOu + ")(uid={user}))"); // TODO: Need to check this on LDAP
            final var connectionConfig = new ConnectionConfig();
            connectionConfig.setConnectTimeout(Duration.ofMillis(500));
            connectionConfig.setResponseTimeout(Duration.ofSeconds(1));
            connectionConfig.setLdapUrl(this.ldapUrl);
            final var connectionFactory = new DefaultConnectionFactory();
            connectionFactory.setConnectionConfig(connectionConfig);
            final var poolConfig = new PoolConfig();
            poolConfig.setMinPoolSize(1);
            poolConfig.setMaxPoolSize(2);
            poolConfig.setValidateOnCheckOut(true);
            poolConfig.setValidateOnCheckIn(true);
            poolConfig.setValidatePeriodically(false);
            final var searchValidator = new SearchValidator();
            final var pruneStrategy = new IdlePruneStrategy();
            final var connectionPool = new BlockingConnectionPool();
            connectionPool.setPoolConfig(poolConfig);
            connectionPool.setBlockWaitTime(Duration.ofSeconds(1));
            connectionPool.setValidator(searchValidator);
            connectionPool.setPruneStrategy(pruneStrategy);
            connectionPool.setConnectionFactory(connectionFactory);
            connectionPool.initialize();
            final var pooledConnectionFactory = new PooledConnectionFactory();
            pooledConnectionFactory.setConnectionPool(connectionPool);
            dnResolver.setConnectionFactory(pooledConnectionFactory);
            final var handler = new PooledBindAuthenticationHandler();
            handler.setConnectionFactory(pooledConnectionFactory);
            final var ldaptiveAuthenticator = new org.ldaptive.auth.Authenticator();
            ldaptiveAuthenticator.setDnResolver(dnResolver);
            ldaptiveAuthenticator.setAuthenticationHandler(handler);

            authenticator = new LdapProfileService(pooledConnectionFactory, ldaptiveAuthenticator, this.ldapBaseUserDn);
        }

        // If we're IP whitelisting it, we just need to check the webcontext. Otherwise we'll want to
        // add tokens and such
        basicAuthClient.setAuthenticator(authenticator);
        basicAuthClient.setAuthorizationGenerator((webContext, profile) -> {
            // TODO: need to check the ldap profile for the right OU, but if that filter is right, then we won't need to
            //  We're assuming that there is only one role on SOAD coming from LDAP right now, if more, we'll need to
            //  inspect the profile to see what attributes are given.
            profile.addRole(AuthService.Roles.ADMIN);
            return profile;
        });
        return basicAuthClient;
    }

    // For use with internal endpoints, basically the Sonatype endpoint
    // IP Whitelisted.
    @Provides
    @Named(AuthService.Providers.WEBHOOK)
    protected DirectClient<TokenCredentials, CommonProfile> provideInternalAuthenticatorClient() {
        final var ipClient = new IpClient();
        ipClient.setName(AuthService.Providers.WEBHOOK);
        ipClient.setAuthenticator(new IpRegexpAuthenticator(this.ipAddressWhitelistRegex));
        ipClient.setProfileCreator((tokenCredentials, webContext) -> {
            final var profile = new CommonProfile();
            profile.setId("webhook");
            profile.addRole(AuthService.Roles.WEBHOOK);
            profile.setClientName("SOAD Webhook");
            return profile;
        });
        return ipClient;
    }

    // Provides the JWT client - mostly taken from example.
    @Provides
    @Named(AuthService.Providers.JWT)
    protected DirectClient<TokenCredentials, CommonProfile> provideHeaderJwtClient() throws ParseException, JOSEException {
        final var headerClient = new HeaderClient();
        headerClient.setName(AuthService.Providers.JWT);
        headerClient.setHeaderName(HttpConstants.AUTHORIZATION_HEADER);
        headerClient.setPrefixHeader(HttpConstants.BEARER_HEADER_PREFIX);

        final var jwtAuthenticator = new JwtAuthenticator();
        jwtAuthenticator.addSignatureConfiguration(this.secretSignatureConfiguration);
//        jwtAuthenticator.addEncryptionConfiguration(this.secretEncryptionConfiguration);
        headerClient.setAuthenticator(jwtAuthenticator); // this should provide the correct profile automagically.
        headerClient.setName(AuthService.Providers.JWT);
        return headerClient;
    }

    @Provides
    @SOADAuth
    protected JwtGenerator<CommonProfile> provideJwtGenerator() {
        final var generator = new JwtGenerator<>(this.secretSignatureConfiguration);
        generator.setExpirationTime(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        return generator;
    }

    @Provides
    @SOADAuth
    protected Config configProvider(
            @Named(AuthService.Providers.JWT) final DirectClient<TokenCredentials, CommonProfile> client,
            @Named(AuthService.Providers.LDAP) final DirectClient<UsernamePasswordCredentials, CommonProfile> ldapClient,
            @Named(AuthService.Providers.WEBHOOK) final DirectClient<TokenCredentials, CommonProfile> webhookClient) {
        final var config = new Config(client, ldapClient, webhookClient);
        config.getClients().setDefaultSecurityClients(client.getName());
        config.addAuthorizer(AuthService.Roles.ADMIN, this.adminRoleAuthorizer);
        config.addAuthorizer(AuthService.Roles.WEBHOOK, this.webhookAuthorizer);
        return config;
    }

}
