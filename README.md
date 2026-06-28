# DB pool in tests

[![Maven CI](https://github.com/andrei-punko/db-pool-in-tests/actions/workflows/maven.yml/badge.svg)](https://github.com/andrei-punko/db-pool-in-tests/actions/workflows/maven.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

Repository: [github.com/andrei-punko/db-pool-in-tests](https://github.com/andrei-punko/db-pool-in-tests)

A multi-module Maven project: a **library** that provides a pool of isolated PostgreSQL databases for integration tests (fast cloning from a
template database), plus an **example** Spring Boot + Testcontainers setup.

- **Java 21**,
- **Spring Boot 3.5.x**
- **PostgreSQL 16** via Testcontainers (`PostgresContainerFactory.IMAGE` — default `huntress/postgres-partman:16.8`, not a vanilla
  `postgres` image)

## Structure

| Module            | Artifact                      | Purpose                                                                                        |
|-------------------|-------------------------------|------------------------------------------------------------------------------------------------|
| `db-pool-library` | `by.andd3dfx:db-pool-library` | Pool implementation, `DataSource` routing, Testcontainers helpers, JUnit extension for metrics |
| `db-pool-example` | `by.andd3dfx:db-pool-example` | Minimal app and integration tests that demonstrate typical wiring                              |

The root `pom.xml` is an aggregator (`packaging pom`) and contains shared `dependencyManagement` for the Testcontainers BOM.

## Build

From the project directory:

```bash
mvn clean verify
```

- Builds the library first, then the example (4 integration tests in `db-pool-example`).
- The example requires **Docker** (or a compatible runtime) for Testcontainers.

Install only the library into the local repository:

```bash
mvn clean install -pl db-pool-library
```

## How it works

1. A single “system” database is provided (via Testcontainers in the example).
2. A **template** database `integration_template_db` is created:

- `classpath:/db/db-init.sql` from the library (PostgreSQL extensions only: `btree_gist`, `pg_stat_statements`)
- then extended via a **`TestDatabaseSchemaPreparer`** bean (tables, seed data, migrations)
- then finalized (`VACUUM FULL`, `CHECKPOINT`, `datistemplate = true`, warmup clone/drop)

3. A background worker fills a pool of **clones** (`CREATE DATABASE … WITH TEMPLATE … STRATEGY FILE_COPY`). Each test gets its own database;
   after the test it is released and scheduled for deferred drop.

To enable per-test DB name substitution, the JDBC URL must contain a segment like `/test_<anything>` — `DataSourceFactory` replaces that
segment with the assigned clone DB name.

## Example module (reference wiring)

Start here when adopting the library. Key files in `db-pool-example`:

| File                                                 | Role                                                                              |
|------------------------------------------------------|-----------------------------------------------------------------------------------|
| `ExampleDbPoolDemoApplication`                       | `@Import(DbPoolTestSupportConfiguration.class)` on the Spring Boot app            |
| `ExampleDbPoolTestConfiguration`                     | `@Bean TestDatabaseSchemaPreparer` → runs `schema.sql` + `seed-data.sql`          |
| `BaseDbPoolIntegrationTest`                          | Base IT class: `@DynamicPropertySource`, pool release, optional metrics extension |
| `src/test/resources/application-it.properties`       | Datasource URL, Hikari schema, logging                                            |
| `CustomerReadDbPoolTest` / `CustomerWriteDbPoolTest` | Read/write against a pooled clone                                                 |
| `CustomerIsolationDbPoolTest`                        | Two ordered tests prove each method gets a fresh database                         |

Example base test class:

```java

@SpringBootTest(classes = ExampleDbPoolDemoApplication.class)
@Import(ExampleDbPoolTestConfiguration.class)
@ActiveProfiles({"it", "testcontainer"})
@ExtendWith(DatabasePoolTimeLoggingExtension.class)
public abstract class BaseDbPoolIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        ContainersLifecycleSupport.postgresqlProperties(registry);
    }

    @AfterEach
    void releaseDb() {
        databasePoolLifecycleService.releaseCurrentDatabase();
    }
}
```

Required test properties (`application-it.properties`):

```properties
# /test_... segment is required for per-test DB name substitution
spring.datasource.url=jdbc:postgresql://localhost:5432/test_db
spring.datasource.username=test
spring.datasource.password=test
spring.datasource.hikari.schema=public
```

Surefire must expose profile **`it`** as a **system property** (the library checks `System.getProperty("spring.profiles.active")` for the
word `it`):

```xml

<systemPropertyVariables>
  <spring.profiles.active>it,testcontainer</spring.profiles.active>
</systemPropertyVariables>
```

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

2. Enable the pool only under profile **`it`**: import `DbPoolTestSupportConfiguration` on your test application or a `@TestConfiguration`:

```java

@SpringBootApplication
@Import(DbPoolTestSupportConfiguration.class)
public class MyApplicationUnderTest {
}
```

3. Register Testcontainers JDBC properties in a base test class:

```java

@DynamicPropertySource
static void postgresqlProperties(DynamicPropertyRegistry registry) {
    ContainersLifecycleSupport.postgresqlProperties(registry);
}
```

Set `spring.profiles.active` in Surefire (see above) or in your IDE run configuration.

4. Provide **`application-it.properties`** (or equivalent) with at least:

- `spring.datasource.url` containing `/test_…`
- `spring.datasource.username` / `password`
- **`spring.datasource.hikari.schema`** (used when creating the template and routing connections)

5. Implement **`TestDatabaseSchemaPreparer`** for your tables and seed data after `db-init.sql`. If you omit the bean, a no-op
   implementation is used.

6. Optional: **`@ExtendWith(DatabasePoolTimeLoggingExtension.class)`** logs **`[db-pool-stats]`** after each test method and *
   *`[db-creation-stats]`** after the test class. Enable `INFO` for package `by.andd3dfx`.

7. After each test, call **`DatabasePoolLifecycleService.releaseCurrentDatabase()`** (typically in `@AfterEach` on a base class).

### Sequential execution

The pool tracks one database per test flow and is designed for **sequential** execution. Do not run pooled integration tests in parallel
without thread-local pool assignment. In JUnit 5, use `@Execution(ExecutionMode.SAME_THREAD)` on the base test class and keep Surefire/JUnit
parallel settings disabled.

## Test naming and Maven

In Spring Boot defaults, classes named **`*IT`** are intended for **Failsafe** (`integration-test`), not Surefire (`test`). The example uses
**`*Test`** so tests run on **`mvn test`** / **`mvn verify`** without extra plugin configuration.

## Main extension points

- **`TestDatabaseSchemaPreparer`**: custom SQL/migrations on top of the template.
- **`PostgresContainerFactory.IMAGE`**: Postgres image version (often pinned in CI).
- **`db/db-init.sql`** (in the library): minimal extension bootstrap; application schema belongs in your `TestDatabaseSchemaPreparer` or
  additional scripts under `src/test/resources`.
