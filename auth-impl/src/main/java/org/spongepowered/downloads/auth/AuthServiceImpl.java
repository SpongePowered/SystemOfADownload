package org.spongepowered.downloads.auth;

import akka.NotUsed;
import com.google.inject.Inject;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import org.pac4j.core.config.Config;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.jwt.profile.JwtGenerator;
import org.pac4j.lagom.javadsl.SecuredService;
import org.spongepowered.downloads.auth.api.AuthService;
import org.spongepowered.downloads.auth.api.AuthenticationRequest;
import org.spongepowered.downloads.auth.api.SOADAuth;
import org.spongepowered.downloads.utils.AuthUtils;
import org.taymyr.lagom.javadsl.openapi.AbstractOpenAPIService;

import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

public final class AuthServiceImpl extends AbstractOpenAPIService implements AuthService, SecuredService {

    private final Config securityConfig;
    private final JwtGenerator<CommonProfile> profileJwtGenerator;

    @Inject
    public AuthServiceImpl(@SOADAuth final Config config, @SOADAuth final JwtGenerator<CommonProfile> profileJwtGenerator) {
        this.securityConfig = config;
        this.profileJwtGenerator = profileJwtGenerator;
    }

    @Override
    public ServiceCall<NotUsed, AuthenticationRequest.Response> login() {
        return this.authorize(Providers.LDAP, AuthUtils.Roles.ADMIN, profile -> {
            this.profileJwtGenerator.setExpirationTime(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)));
            return notUsed -> CompletableFuture.completedFuture(new AuthenticationRequest.Response(this.profileJwtGenerator.generate(profile)));
        });
    }

    @Override
    public ServiceCall<NotUsed, NotUsed> logout() {
        // TODO - if it's even possible
        return notUsed -> CompletableFuture.completedFuture(NotUsed.getInstance());
    }

    @Override
    public Config getSecurityConfig() {
        return this.securityConfig;
    }
}
