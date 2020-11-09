package org.spongepowered.downloads.auth.impl.identity;

@FunctionalInterface
public interface IdentityProvider {

    boolean isUser(final String user, final String password);

}
