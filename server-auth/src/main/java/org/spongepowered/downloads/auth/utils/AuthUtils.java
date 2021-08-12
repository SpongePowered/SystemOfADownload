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
package org.spongepowered.downloads.auth.utils;

import io.vavr.collection.List;
import org.pac4j.core.authorization.authorizer.Authorizer;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.http.client.direct.HeaderClient;
import org.pac4j.jwt.config.encryption.EncryptionConfiguration;
import org.pac4j.jwt.config.encryption.SecretEncryptionConfiguration;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.config.signature.SignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import org.pac4j.jwt.profile.JwtGenerator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public final record AuthUtils(String encryptionSecret, String signatureSecret,
                              String nexusWebhookSecret, String internalHeaderSecret,
                              String internalHeaderKey) {

    @SuppressWarnings("rawtypes")
    public Config config(final Client... additionalClients) {
        final var jwtClient = this.createJwtClient();
        final var config = new Config(List.<Client>of(jwtClient).appendAll(List.of(additionalClients)).asJava());
        config.getClients().setDefaultSecurityClients(jwtClient.getName());
        this.setAuthorizers(config);
        return config;
    }


    private DirectClient<TokenCredentials, CommonProfile> createJwtClient() {
        final var headerClient = new HeaderClient();
        headerClient.setName(AuthUtils.Types.JWT);
        headerClient.setHeaderName(HttpConstants.AUTHORIZATION_HEADER);
        headerClient.setPrefixHeader(HttpConstants.BEARER_HEADER_PREFIX);

        final var jwtAuthenticator = new JwtAuthenticator();
        if (!this.signatureSecret.isBlank()) {
            jwtAuthenticator.addSignatureConfiguration(this.getSignatureConfiguration());
        }
        if (!this.encryptionSecret.isBlank()) {
            jwtAuthenticator.addEncryptionConfiguration(this.getEncryptionConfiguration());
        }
        headerClient.setAuthenticator(jwtAuthenticator); // this should provide the correct profile automagically.
        headerClient.setName(AuthUtils.Types.JWT);
        return headerClient;
    }

    public JwtGenerator<CommonProfile> createJwtGenerator() {
        final var generator = new JwtGenerator<>();
        if (!this.signatureSecret.isBlank()) {
            generator.setSignatureConfiguration(this.getSignatureConfiguration());
        }
        if (!this.encryptionSecret.isBlank()) {
            generator.setEncryptionConfiguration(this.getEncryptionConfiguration());
        }
        generator.setExpirationTime(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        return generator;
    }

    private void setAuthorizers(final Config config) {
        config.addAuthorizer(Roles.ADMIN, Authorizers.ADMIN);
        config.addAuthorizer(Roles.WEBHOOK, Authorizers.WEBHOOK);
    }

    private EncryptionConfiguration getEncryptionConfiguration() {
        return new SecretEncryptionConfiguration(this.encryptionSecret);
    }

    private SignatureConfiguration getSignatureConfiguration() {
        return new SecretSignatureConfiguration(this.signatureSecret);
    }

    private SignatureConfiguration getSonatypeSignatureConfiguration() {
        return new SecretSignatureConfiguration(this.nexusWebhookSecret);
    }

    public static AuthUtils configure(com.typesafe.config.Config config) {
        final var authConfig = config.getConfig("systemofadownload.auth.secrets");
        final var encryptionSecret = authConfig.getString("encryption");
        final var signatureSecret = authConfig.getString("signature");
        final var nexusWebhookSecret = authConfig.getString("nexus-webhook");
        final var internalHeaderKey = authConfig.getString("internal-header");
        final var internalHeaderSecret = authConfig.getString("internal-secret");
        return new AuthUtils(
            encryptionSecret, signatureSecret, nexusWebhookSecret, internalHeaderSecret, internalHeaderKey);
    }

    static final class Authorizers {
        static final Authorizer<CommonProfile> ADMIN =
            (webContext, list) -> list.stream().anyMatch(
                x -> !x.isExpired() && x.getRoles().contains(AuthUtils.Roles.ADMIN));
        static final Authorizer<CommonProfile> WEBHOOK =
            (webContext, list) -> list.stream().anyMatch(
                x -> !x.isExpired() && x.getRoles().contains(AuthUtils.Roles.WEBHOOK));
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
