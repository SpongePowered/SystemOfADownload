/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads.auth;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.lightbend.lagom.javadsl.client.ConfigurationServiceLocator;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.ldaptive.ConnectionConfig;
import org.ldaptive.DefaultConnectionFactory;
import org.ldaptive.auth.FormatDnResolver;
import org.ldaptive.auth.PooledBindAuthenticationHandler;
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
import org.spongepowered.downloads.auth.utils.AuthUtils;
import play.Environment;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

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

    private final boolean useDummyCredentials;
    private final String ldapUrl;
    private final String ldapBaseUserDn;
    private final String ldapSoadOu;

    private final Duration connectionTimeout;
    private final Duration responseTimeout;
    private final Duration blockWaitTime;

    private final Environment environment;
    private final com.typesafe.config.Config config;
    private final AuthUtils auth;

    public AuthModule(final Environment environment, final com.typesafe.config.Config config) {
        this.environment = environment;
        this.config = config.getConfig("systemofadownload.auth");
        final var ldap = this.config.getConfig("ldap");
        this.useDummyCredentials = this.config.getBoolean("use-dummy-ldap");
        this.ldapUrl = ldap.getString("url");
        this.ldapBaseUserDn = ldap.getString("base-user-on");
        this.ldapSoadOu = ldap.getString("soad-ou");
        this.connectionTimeout = Duration.ofMillis(ldap.getDuration("connection-timeout", TimeUnit.MILLISECONDS));
        this.responseTimeout = Duration.ofSeconds(ldap.getDuration("response-timeout", TimeUnit.SECONDS));
        this.blockWaitTime = Duration.ofSeconds(ldap.getDuration("wait-time", TimeUnit.SECONDS));
        this.auth = AuthUtils.configure(config);
    }

    @Override
    protected void configure() {
        if (this.environment.isProd()) {
            this.bind(ServiceLocator.class).to(ConfigurationServiceLocator.class);
        }
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
                    profile.addRole(AuthUtils.Roles.ADMIN);
                    return;
                }
                throw new CredentialsException("Incorrect username and password.");
            });
        } else {
            // http://www.pac4j.org/3.4.x/docs/authenticators/ldap.html
            // This could probably be improved... but the documentation leaves something to be desired
            final var dnResolver = new FormatDnResolver();
            dnResolver.setFormat("cn=%s," + this.ldapBaseUserDn);
            final var connectionConfig = new ConnectionConfig();
            connectionConfig.setConnectTimeout(this.connectionTimeout);
            connectionConfig.setResponseTimeout(this.responseTimeout);
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
            connectionPool.setBlockWaitTime(this.blockWaitTime);
            connectionPool.setValidator(searchValidator);
            connectionPool.setPruneStrategy(pruneStrategy);
            connectionPool.setConnectionFactory(connectionFactory);
            connectionPool.initialize();
            final var pooledConnectionFactory = new PooledConnectionFactory();
            pooledConnectionFactory.setConnectionPool(connectionPool);
           // dnResolver.setConnectionFactory(pooledConnectionFactory);
            final var handler = new PooledBindAuthenticationHandler();
            handler.setConnectionFactory(pooledConnectionFactory);
            final var ldaptiveAuthenticator = new org.ldaptive.auth.Authenticator();
            ldaptiveAuthenticator.setDnResolver(dnResolver);
            ldaptiveAuthenticator.setAuthenticationHandler(handler);

            authenticator = new LdapProfileService(pooledConnectionFactory, ldaptiveAuthenticator, "ou", this.ldapBaseUserDn);
        }

        // If we're IP whitelisting it, we just need to check the webcontext. Otherwise we'll want to
        // add tokens and such
        basicAuthClient.setAuthenticator(authenticator);
        basicAuthClient.setAuthorizationGenerator((webContext, profile) -> {
            final var ouAttr = profile.getAttribute("ou");
            final boolean isAdmin;
            if (ouAttr instanceof String) {
                isAdmin = ouAttr.equals(this.ldapSoadOu);
            } else if (ouAttr instanceof Collection) {
                isAdmin = ((Collection<?>) ouAttr).contains(this.ldapSoadOu);
            } else {
                isAdmin = false;
            }

            if (isAdmin) {
                profile.addRole(AuthUtils.Roles.ADMIN);
            }
            return profile;
        });
        return basicAuthClient;
    }

    @Provides
    @SOADAuth
    protected JwtGenerator<CommonProfile> provideJwtGenerator() {
        return this.auth.createJwtGenerator();
    }

    @Provides
    @SOADAuth
    protected Config configProvider(@Named(AuthService.Providers.LDAP) final DirectClient<UsernamePasswordCredentials, CommonProfile> ldapClient) {
        return this.auth.config(ldapClient);
    }

}
