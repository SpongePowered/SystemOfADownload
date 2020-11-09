package org.spongepowered.downloads.auth.impl;

import akka.Done;
import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import org.pac4j.core.config.Config;
import org.pac4j.lagom.javadsl.SecuredService;
import org.spongepowered.downloads.auth.api.AuthService;
import org.spongepowered.downloads.auth.api.AuthenticationRequest;

import java.util.concurrent.CompletableFuture;

public final class AuthServiceImpl implements AuthService, SecuredService {

    private final Config securityConfig;

    @Inject
    public AuthServiceImpl(final Config config) {
        this.securityConfig = config;
    }

    @Override
    public ServiceCall<AuthenticationRequest.Request, AuthenticationRequest.Response> login() {
        return this.authorize(AuthModule.LDAP_PROVIDER, profile -> {
            return request -> CompletableFuture.completedFuture(new AuthenticationRequest.Response("test"));
        });
    }

    @Override
    public ServiceCall<NotUsed, NotUsed> logout() {
        // TODO - if it's even possible
        return notUsed -> CompletableFuture.completedFuture(NotUsed.getInstance());
    }

    @Override
    public Config getSecurityConfig() {
        return null;
    }
}
