# DB pool in tests

Многомодульный Maven-проект: **библиотека** для пула изолированных тестовых баз PostgreSQL (клонирование из template) и **пример** подключения в Spring Boot + Testcontainers.

- **Java 21**, **Spring Boot 3.5.x**
- **PostgreSQL 16** в контейнере (образ задаётся в `PostgresContainerFactory`)

## Структура

| Модуль | Артефакт | Назначение |
|--------|-----------|------------|
| `db-pool-library` | `by.andd3dfx:db-pool-library` | Классы пула, маршрутизация `DataSource`, Testcontainers-хелперы, расширение JUnit для логов метрик |
| `db-pool-example` | `by.andd3dfx:db-pool-example` | Минимальное приложение и интеграционный тест, показывающий типичную связку |

Корневой `pom.xml` — агрегатор (`packaging pom`), общий `dependencyManagement` для Testcontainers BOM.

## Сборка

Из каталога проекта:

```bash
mvn clean verify
```

- Собирается библиотека, затем пример.
- Для примеров с Testcontainers нужен **Docker** (или совместимый runtime).

Только библиотека в локальный репозиторий:

```bash
mvn clean install -pl db-pool-library
```

## Идея работы

1. Поднимается одна «системная» БД (часто через Testcontainers).
2. Создаётся **template**-база `integration_template_db`: схема из `classpath:/db/db-init.sql` и опциональная доработка через бин `TestDatabaseSchemaPreparer`.
3. В **фоне** наполняется пул **клонов** (`CREATE DATABASE … WITH TEMPLATE … STRATEGY FILE_COPY`); каждому тесту выдаётся отдельная БД, после теста — освобождение и отложенное удаление.

Имя JDBC URL для подстановки имён клонов должно содержать сегмент вида `/test_<что угодно>` — его `DataSourceFactory` заменяет на имя выданной базы (см. пример в `db-pool-example`).

## Подключение в своём проекте

1. Зависимость (обычно **`scope` `test`**):

```xml
<dependency>
    <groupId>by.andd3dfx</groupId>
    <artifactId>db-pool-library</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

2. Включить конфигурацию пула только в интеграционных тестах — профиль **`it`** и импорт:

```java
@SpringBootApplication
@Import(DbPoolTestSupportConfiguration.class)
public class MyApplicationUnderTest { }
```

или `@Import(DbPoolTestSupportConfiguration.class)` на `@TestConfiguration`.

3. **`ContainersLifecycleSupport`** в тестах читает **system property** `spring.profiles.active` и подставляет URL/username контейнера через `@DynamicPropertySource`. Задайте её в **Surefire** (как в `db-pool-example/pom.xml`) или в IDE для конфигурации запуска.

4. Дополнительная подготовка схемы после `db-init.sql` — свой бин **`TestDatabaseSchemaPreparer`** (если не объявить, используется no-op из конфигурации).

5. По желанию: **`@ExtendWith(DatabasePoolTimeLoggingExtension.class)`** — логи старта/окончания метода, `[db-pool-stats]`, после класса `[db-creation-stats]`. Для видимости включите уровень логов для пакета `by.andd3dfx` (например `INFO`).

6. После каждого теста, если используете пул, вызывайте **`DatabasePoolLifecycleService.releaseCurrentDatabase()`** (последовательный запуск; параллельный — отдельная доработка).

## Имена тестов и Maven

У Spring Boot по умолчанию классы **`*IT`** относятся к **Failsafe** (`integration-test`), а не к Surefire (`test`). Чтобы тесты выполнялись при **`mvn test`**, используйте суффикс **`*Test` / `*Tests`** или настройте плагины явно (см. `db-pool-example`).

## Основные точки расширения

- **`TestDatabaseSchemaPreparer`** — свой SQL/миграции поверх template.
- **`PostgresContainerFactory.IMAGE`** — версия образа Postgres (в CI иногда выносят в свойство).
- Ресурс **`db/db-init.sql`** в библиотеке — минимальный init; при необходимости переопределите или дополните скриптами в своём `test/resources`.