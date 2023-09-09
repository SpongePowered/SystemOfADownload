package org.spongepowered.downloads.test.artifacts.server;

import io.micronaut.context.BeanContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.spongepowered.downloads.artifacts.server.query.meta.ArtifactRepository;
import org.spongepowered.downloads.artifacts.server.query.meta.domain.JpaArtifact;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestResourcesScope("testcontainers")
public class ArtifactRepositoryTest {
    @Inject
    BeanContext context;

    @Inject
    EmbeddedApplication<?> application;

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Test
    public void testItWorks() {
        Assertions.assertTrue(application.isRunning());
    }

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
        Assertions.assertNotNull(liquibaseReport.getChangeSets());
        Assertions.assertEquals(2, liquibaseReport.getChangeSets().size());
    }
    @Serdeable
    static class LiquibaseReport {

        private List<ChangeSet> changeSets;

        public void setChangeSets(List<ChangeSet> changeSets) {
            this.changeSets = changeSets;
        }

        public List<ChangeSet> getChangeSets() {
            return changeSets;
        }
    }

    @Serdeable
    static class ChangeSet {

        private String id;

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    @Test
    public void testAnnotation() {
        final BeanDefinition<ArtifactRepository> beanDefinition = context.getBeanDefinition(ArtifactRepository.class);
        final ExecutableMethod<ArtifactRepository, Object> findByGroupIdAndArtifactId = beanDefinition // (1)
            .getRequiredMethod("findByGroupIdAndArtifactId", String.class, String.class);
        String query = findByGroupIdAndArtifactId // (2)
            .getAnnotationMetadata().stringValue(Query.class) // (3)
            .orElse(null);

        final String expected = "SELECT artifact.\"id\",artifact.\"group_id\",artifact.\"artifact_id\",artifact.\"display_name\",artifact.\"website\",artifact.\"git_repository\",artifact.\"issues\",artifact.\"tag_values\",artifact.\"coordinates\",artifact.\"tag_values_for_reply\" FROM \"artifacts\".\"artifacts\" artifact WHERE (artifact.\"group_id\" = $1 AND artifact.\"artifact_id\" = $2)";
        Assertions.assertEquals( // (4)
            expected, query);
    }

    @Test
    public void testGetArtifact() {
        final ArtifactRepository repo = context.createBean(ArtifactRepository.class);


        final Mono<JpaArtifact> spongevanilla = repo.findByGroupIdAndArtifactId("org.spongepowered", "spongevanilla");
        final JpaArtifact a = spongevanilla.block();

    }



}
