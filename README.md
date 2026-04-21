# DB pool in tests

[![Maven CI](https://github.com/andrei-punko/db-pool-in-tests/actions/workflows/maven.yml/badge.svg)](https://github.com/andrei-punko/db-pool-in-tests/actions/workflows/maven.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

A multi-module Maven project: a **library** that provides a pool of isolated PostgreSQL databases for integration tests (fast cloning from a template database), plus an **example** Spring Boot + Testcontainers setup.

- **Java 21**, **Spring Boot 3.5.x**
- **PostgreSQL 16** in a container (image is set in `PostgresContainerFactory`)

## Structure

| Module | Artifact | Purpose |
|--------|-----------|------------|
| `db-pool-library` | `by.andd3dfx:db-pool-library` | Pool implementation, `DataSource` routing, Testcontainers helpers, JUnit extension for metrics/logging |
| `db-pool-example` | `by.andd3dfx:db-pool-example` | Minimal app and a test that demonstrates typical wiring |

The root `pom.xml` is an aggregator (`packaging pom`) and contains shared `dependencyManagement` for the Testcontainers BOM.

## Build

From the project directory:

```bash
mvn clean verify
```

- Builds the library first, then the example.
- The example requires **Docker** (or a compatible runtime) for Testcontainers.

Install only the library into the local repository:

```bash
mvn clean install -pl db-pool-library
```

## How it works

1. A single “system” database is provided (often via Testcontainers).
2. A **template** database `integration_template_db` is created: schema is applied from `classpath:/db/db-init.sql`, then optionally extended via a `TestDatabaseSchemaPreparer` bean.
3. A background worker fills a pool of **clones** (`CREATE DATABASE … WITH TEMPLATE … STRATEGY FILE_COPY`). Each test gets its own database; after the test it is released and scheduled for deferred drop.

To enable per-test DB name substitution, the JDBC URL must contain a segment like `/test_<anything>` — `DataSourceFactory` replaces that segment with the assigned clone DB name (see `db-pool-example`).

## Using it in your project

1. Add the dependency (typically with **`scope` `test`**):

```xml
<dependency>
    <groupId>by.andd3dfx</groupId>
    <artifactId>db-pool-library</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

2. Enable the pool configuration only for integration tests: use profile **`it`** and import the configuration:

```java
@SpringBootApplication
@Import(DbPoolTestSupportConfiguration.class)
public class MyApplicationUnderTest { }
```

or place `@Import(DbPoolTestSupportConfiguration.class)` on a `@TestConfiguration`.

3. In tests, **`ContainersLifecycleSupport`** checks the **system property** `spring.profiles.active` and registers container URL/username via `@DynamicPropertySource`. Set it in **Surefire** (as in `db-pool-example/pom.xml`) or in your IDE run configuration.

4. Additional schema preparation after `db-init.sql` is done via a **`TestDatabaseSchemaPreparer`** bean (if you don’t provide one, a no-op implementation is used).

5. Optional: **`@ExtendWith(DatabasePoolTimeLoggingExtension.class)`** prints per-test start/finish messages, `[db-pool-stats]` after each test, and `[db-creation-stats]` after the test class. Make sure logging for package `by.andd3dfx` is enabled (e.g. `INFO`).

6. After each test, call **`DatabasePoolLifecycleService.releaseCurrentDatabase()`** if you use the pool (current implementation assumes sequential execution; parallel tests require additional work).

## Test naming and Maven

In Spring Boot defaults, classes named **`*IT`** are intended for **Failsafe** (`integration-test`), not Surefire (`test`). If you want the example to run on **`mvn test`**, use **`*Test` / `*Tests`** naming or configure plugins explicitly (see `db-pool-example`).

## Main extension points

- **`TestDatabaseSchemaPreparer`**: custom SQL/migrations on top of the template.
- **`PostgresContainerFactory.IMAGE`**: Postgres image version (often externalized in CI).
- **`db/db-init.sql`** (in the library): minimal init; override/extend it with additional scripts from your own `test/resources` if needed.