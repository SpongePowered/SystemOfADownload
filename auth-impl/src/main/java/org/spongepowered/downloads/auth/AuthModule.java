package org.spongepowered.downloads.auth;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.ldaptive.ConnectionConfig;
import org.ldaptive.DefaultConnectionFactory;
import org.ldaptive.auth.PooledBindAuthenticationHandler;
import org.ldaptive.auth.SearchDnResolver;
import org.ldaptive.pool.BlockingConnectionPool;
import org.ldaptive.pool.IdlePruneStrategy;
import org.ldaptive.pool.PoolConfig;
import org.ldaptive.pool.PooledConnectionFactory;
import org.ldaptive.pool.SearchValidator;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.http.client.direct.DirectBasicAuthClient;
import org.pac4j.jwt.profile.JwtGenerator;
import org.pac4j.ldap.profile.service.LdapProfileService;
import org.spongepowered.downloads.auth.api.AuthService;
import org.spongepowered.downloads.auth.api.SOADAuth;
import org.spongepowered.downloads.utils.AuthUtils;

import java.time.Duration;
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

    private final boolean useDummyCredentials = Objects.requireNonNullElse(System.getenv("USE_DUMMY_LDAP"), "false").equalsIgnoreCase("true");
    private final String ldapUrl = Objects.requireNonNullElse(System.getenv("LDAP_URL"), "ldap://localhost:389");
    private final String ldapBaseUserDn = Objects.requireNonNullElse(System.getenv("LDAP_BASE_USER_DN"), "dc=spongepowered,dc=org");
    private final String ldapSoadOu = Objects.requireNonNullElse(System.getenv("LDAP_SOAD_OU"), "soad");

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
            profile.addRole(AuthUtils.Roles.ADMIN);
            return profile;
        });
        return basicAuthClient;
    }

    @Provides
    @SOADAuth
    protected JwtGenerator<CommonProfile> provideJwtGenerator() {
        return AuthUtils.createJwtGenerator();
    }

    @Provides
    @SOADAuth
    protected Config configProvider(@Named(AuthService.Providers.LDAP) final DirectClient<UsernamePasswordCredentials, CommonProfile> ldapClient) {
        return AuthUtils.createConfig(ldapClient);
    }

}
