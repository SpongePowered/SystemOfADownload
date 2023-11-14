# SystemOfADownload

The metadata generator webapp that serves up enhanced information
and "tagging" for artifacts from a Maven repository. This is intended to
serve as a backend to power the data to generate for serving an enhanced
downloads website.

## Requirements

- Java 21 (GraalVM optional to build native images)
- Docker
- terraform (if you want to deploy to a kubernetes cluster)

## Technologies in use
### Micronaut 4.1.3

- [User Guide](https://docs.micronaut.io/4.1.3/guide/index.html)
- [API Reference](https://docs.micronaut.io/4.1.3/api/index.html)
- [Configuration Reference](https://docs.micronaut.io/4.1.3/guide/configurationreference.html)
- [Micronaut Guides](https://guides.micronaut.io/index.html)
---

- [Shadow Gradle Plugin](https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow)
- [Micronaut Gradle Plugin documentation](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/)
- [GraalVM Gradle Plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
#### Feature test-resources documentation

- [Micronaut Test Resources documentation](https://micronaut-projects.github.io/micronaut-test-resources/latest/guide/)


#### Feature r2dbc documentation

- [Micronaut R2DBC documentation](https://micronaut-projects.github.io/micronaut-r2dbc/latest/guide/)

- [https://r2dbc.io](https://r2dbc.io)


#### Feature github-workflow-graal-docker-registry documentation

- [https://docs.github.com/en/free-pro-team@latest/actions](https://docs.github.com/en/free-pro-team@latest/actions)


#### Feature security-ldap documentation

- [Micronaut Security LDAP documentation](https://micronaut-projects.github.io/micronaut-security/latest/guide/index.html#ldap)


#### Feature discovery-kubernetes documentation

- [Micronaut Kubernetes Service Discovery documentation](https://micronaut-projects.github.io/micronaut-kubernetes/latest/guide/#service-discovery)


#### Feature micronaut-aot documentation

- [Micronaut AOT documentation](https://micronaut-projects.github.io/micronaut-aot/latest/guide/)


#### Feature liquibase documentation

- [Micronaut Liquibase Database Migration documentation](https://micronaut-projects.github.io/micronaut-liquibase/latest/guide/index.html)

- [https://www.liquibase.org/](https://www.liquibase.org/)


#### Feature cache-caffeine documentation

- [Micronaut Caffeine Cache documentation](https://micronaut-projects.github.io/micronaut-cache/latest/guide/index.html)

- [https://github.com/ben-manes/caffeine](https://github.com/ben-manes/caffeine)


#### Feature serialization-jackson documentation

- [Micronaut Serialization Jackson Core documentation](https://micronaut-projects.github.io/micronaut-serialization/latest/guide/)


#### Feature data-r2dbc documentation

- [Micronaut Data R2DBC documentation](https://micronaut-projects.github.io/micronaut-data/latest/guide/#dbc)

- [https://r2dbc.io](https://r2dbc.io)


#### Feature jdbc-hikari documentation

- [Micronaut Hikari JDBC Connection Pool documentation](https://micronaut-projects.github.io/micronaut-sql/latest/guide/index.html#jdbc)




[LagomFramework]:https://lagomframework.com/
[Event Source]:https://docs.microsoft.com/en-us/azure/architecture/patterns/event-sourcing
[CQRS]:https://docs.microsoft.com/en-us/azure/architecture/patterns/cqrs
[Play]:https://www.playframework.com
[Cassandra]:https://cassandra.apache.org
[Sonatype Nexus]:https://www.sonatype.com/nexus/repository-pro
[Saga]:https://docs.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga
[`SecuredService`]:https://github.com/pac4j/lagom-pac4j/blob/master/shared/src/main/java/org/pac4j/lagom/javadsl/SecuredService.java
