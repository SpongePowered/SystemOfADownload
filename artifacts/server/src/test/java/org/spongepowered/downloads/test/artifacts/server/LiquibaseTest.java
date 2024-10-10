package org.spongepowered.downloads.test.artifacts.server;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestResourcesScope("testcontainers")
public class LiquibaseTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Test
    void migrationsAreExposedViaAndEndpoint() {
        BlockingHttpClient client = httpClient.toBlocking();

        HttpResponse<List<LiquibaseReport>> response = client.exchange(
            HttpRequest.GET("/liquibase"),
            Argument.listOf(LiquibaseReport.class)
        );
        Assertions.assertEquals(HttpStatus.OK, response.status());

        LiquibaseReport liquibaseReport = response.body().get(0);
        Assertions.assertNotNull(liquibaseReport);
        Assertions.assertNotNull(liquibaseReport.changeSets());
        Assertions.assertEquals(3, liquibaseReport.changeSets().size());
    }
    @Serdeable
    record LiquibaseReport(List<ChangeSet> changeSets) {

    }

    @Serdeable
    record ChangeSet( String id) {
    }

}
