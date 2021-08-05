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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class AuthServiceImpl extends AbstractOpenAPIService implements AuthService, SecuredService {

    private final Config securityConfig;
    private final JwtGenerator<CommonProfile> profileJwtGenerator;
    private final Duration expirationTime;

    @Inject
    public AuthServiceImpl(
        @SOADAuth final Config config, @SOADAuth final JwtGenerator<CommonProfile> profileJwtGenerator,
        com.typesafe.config.Config applicationConfig
    ) {
        this.securityConfig = config;
        this.profileJwtGenerator = profileJwtGenerator;
        this.expirationTime = Duration.ofSeconds(
            applicationConfig.getDuration("systemofadownload.auth.expiration", TimeUnit.SECONDS));
    }

    @Override
    public ServiceCall<NotUsed, AuthenticationRequest.Response> login() {
        return this.authorize(Providers.LDAP, AuthUtils.Roles.ADMIN, profile -> {
            this.profileJwtGenerator.setExpirationTime(Date.from(Instant.now().plus(this.expirationTime)));
            return notUsed -> CompletableFuture.completedFuture(new AuthenticationRequest.Response(this.profileJwtGenerator.generate(profile)));
        });
    }

    @Override
    public ServiceCall<NotUsed, NotUsed> logout() {
        // TODO - if it's even possible
        return this.authorize(AuthUtils.Types.JWT, AuthUtils.Roles.ADMIN, profile -> {
            return notUsed -> CompletableFuture.completedFuture(NotUsed.getInstance());
        });
    }

    @Override
    public Config getSecurityConfig() {
        return this.securityConfig;
    }
}
