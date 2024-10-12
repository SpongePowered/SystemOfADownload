package org.spongepowered.downloads.artifact.api.test;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.downloads.artifact.api.mutation.Update;

@MicronautTest(startApplication = false)
public class UpdateValidationTest {

    @Inject
    Validator validator;

    @ParameterizedTest
    @ValueSource(strings = {
        "htp://www.example.com",          // Incorrect protocol
        "http://example..com",            // Double dot in domain
        "http://.example.com",            // Leading dot in domain
        "http://example_com",             // Underscore in domain
        "http://example.com,com",         // Comma in domain
        "http://example..com/page",       // Double dot in URL path
        "://example.com",                 // Missing protocol
        "http://example .com",            // Space in domain
        "http:///example.com",            // Triple slash after protocol
        "http://example.com:-80",         // Negative port number
        "http://example..com",            // Repeated dot in domain name
        "http://-example.com",            // Leading hyphen in domain name
        "http://example.com/invalid|character", // Invalid character '|'
        "ftp://example.com",              // Unsupported protocol (if only http and https are allowed)
        "http://256.256.256.256",         // Invalid IP address
        "http://.com",                    // Only TLD without domain name
        "http://example..com../page",     // Multiple double dots in domain and path
        "http://example!.com",            // Invalid character '!'
        "http://example@com",             // '@' instead of '.'
        "http://www.exam_ple.com"         // Underscore in the subdomain
    })
    void testInvalidUrls(String url) {
        assertFalse(validator.validate(new Update.Website(url)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://github.com/user/repo.git",       // HTTPS with .git
        "https://github.com/user/repo",           // HTTPS without .git
        "git://github.com/user/repo.git",         // Git protocol with .git
        "git://github.com/user/repo",             // Git protocol without .git
        "git@github.com:user/repo.git",           // SSH with .git
        "git@github.com:user/repo",               // SSH without .git
        "https://gitlab.com/organization/project.git", // HTTPS with .git
        "https://bitbucket.org/team/repo.git"     // HTTPS with .git on Bitbucket
    })
    void testValidGitUrls(String url) {
        assertTrue(validator.validate(new Update.GitRepository(url)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://github.com/user/repo.git",        // Invalid protocol (http instead of https)
        "https//github.com/user/repo.git",        // Missing colon in https protocol
        "git:/github.com/user/repo.git",          // Missing slash after git:
        "https://github.com/user/repo.tar.gz",    // Incorrect file extension
        "https://github.com/user/repo/",          // Trailing slash at the end
        "ftp://github.com/user/repo.git",         // Unsupported protocol (ftp)
        "git@github.com:user",                    // Missing repository name
        "github.com:user/repo.git",               // Missing protocol
        "https://github.com/user/repo.git/extra", // Extra path segment after .git
        "git@github.com:user/repo?.git",          // Special character '?' in URL
        "git@github.com:user/repo@",              // Ending with '@'
        "git://github.com/user/repo/repo.git",    // Extra path segment before .git
        "https://github.com/user/repo.git repo",  // Space in URL
        "git@github.com:user//repo.git"           // Double slashes in the URL
    })
    void testInvalidGitUrls(String url) {
        assertFalse(validator.validate(new Update.GitRepository(url)).isEmpty());
    }

}
