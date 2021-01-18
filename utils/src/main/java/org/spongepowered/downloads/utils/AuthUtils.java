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
package org.spongepowered.downloads.utils;

import io.vavr.collection.List;
import org.pac4j.core.authorization.authorizer.Authorizer;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.http.client.direct.HeaderClient;
import org.pac4j.http.client.direct.IpClient;
import org.pac4j.http.credentials.authenticator.IpRegexpAuthenticator;
import org.pac4j.jwt.config.encryption.EncryptionConfiguration;
import org.pac4j.jwt.config.encryption.SecretEncryptionConfiguration;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.config.signature.SignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import org.pac4j.jwt.profile.JwtGenerator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

public final class AuthUtils {

    private static final String IP_ADDRESS_WHITELIST_REGEX =
            Objects.requireNonNullElse(System.getenv("IP_WHITELIST"), "(127\\.0\\.0\\.1|localhost)");
    private static final String ENCRYPTION_SECRET = System.getenv("JWT-ENCRYPTION-SECRET");
    private static final String SIGNATURE_SECRET = System.getenv("JWT-SIGNATURE-SECRET");

    @SuppressWarnings("rawtypes")
    public static Config createConfig(final Client... additionalClients) {
        final var jwtClient = AuthUtils.createJwtClient();
        final var ipClient = AuthUtils.createIpWhitelistClient();
        final var config = new Config(List.<Client>of(jwtClient, ipClient).appendAll(List.of(additionalClients)).asJava());
        config.getClients().setDefaultSecurityClients(jwtClient.getName());
        AuthUtils.setAuthorizers(config);
        return config;
    }

    public static DirectClient<TokenCredentials, CommonProfile> createJwtClient() {
        final var headerClient = new HeaderClient();
        headerClient.setName(AuthUtils.Types.JWT);
        headerClient.setHeaderName(HttpConstants.AUTHORIZATION_HEADER);
        headerClient.setPrefixHeader(HttpConstants.BEARER_HEADER_PREFIX);

        final var jwtAuthenticator = new JwtAuthenticator();
        if (AuthUtils.SIGNATURE_SECRET != null) {
            jwtAuthenticator.addSignatureConfiguration(AuthUtils.getSignatureConfiguration());
        }
        if (AuthUtils.ENCRYPTION_SECRET != null) {
            jwtAuthenticator.addEncryptionConfiguration(AuthUtils.getEncryptionConfiguration());
        }
        headerClient.setAuthenticator(jwtAuthenticator); // this should provide the correct profile automagically.
        headerClient.setName(AuthUtils.Types.JWT);
        return headerClient;
    }

    public static DirectClient<TokenCredentials, CommonProfile> createIpWhitelistClient() {
        final var ipClient = new IpClient();
        ipClient.setName(AuthUtils.Types.WEBHOOK);
        ipClient.setAuthenticator(new IpRegexpAuthenticator(AuthUtils.IP_ADDRESS_WHITELIST_REGEX));
        ipClient.setProfileCreator((tokenCredentials, webContext) -> {
            final var profile = new CommonProfile();
            profile.setId("webhook");
            profile.addRole(AuthUtils.Roles.WEBHOOK);
            profile.setClientName("SOAD Webhook");
            return profile;
        });
        return ipClient;
    }

    public static JwtGenerator<CommonProfile> createJwtGenerator() {
        final var generator = new JwtGenerator<>();
        if (AuthUtils.SIGNATURE_SECRET != null) {
            generator.setSignatureConfiguration(AuthUtils.getSignatureConfiguration());
        }
        if (AuthUtils.ENCRYPTION_SECRET != null) {
            generator.setEncryptionConfiguration(AuthUtils.getEncryptionConfiguration());
        }
        generator.setExpirationTime(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        return generator;
    }

    public static void setAuthorizers(final Config config) {
        config.addAuthorizer(Roles.ADMIN, Authorizers.ADMIN);
        config.addAuthorizer(Roles.WEBHOOK, Authorizers.WEBHOOK);
    }

    private static EncryptionConfiguration getEncryptionConfiguration() {
        return new SecretEncryptionConfiguration(AuthUtils.ENCRYPTION_SECRET);
    }

    private static SignatureConfiguration getSignatureConfiguration() {
        return new SecretSignatureConfiguration(AuthUtils.SIGNATURE_SECRET);
    }

    private AuthUtils() {}

    static final class Authorizers {
        static final Authorizer<CommonProfile> ADMIN =
                (webContext, list) -> list.stream().anyMatch(x -> !x.isExpired() && x.getRoles().contains(AuthUtils.Roles.ADMIN));
        static final Authorizer<CommonProfile> WEBHOOK =
                (webContext, list) -> list.stream().anyMatch(x -> !x.isExpired() && x.getRoles().contains(AuthUtils.Roles.WEBHOOK));
    }

    public static final class Types {
        public static final String JWT = "jwt";
        public static final String WEBHOOK = "internal";
    }

    public static final class Roles {
        public static final String ADMIN = "soad_admin";
        public static final String WEBHOOK = "soad_webhook";
    }
}
