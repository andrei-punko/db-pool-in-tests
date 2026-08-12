package by.andd3dfx.db;

import by.andd3dfx.config.TestDatabaseSchemaPreparer;
import by.andd3dfx.sql.SqlSupport;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Creates and manages template database used as a source for fast per-test database cloning.
 * <p>
 * Responsibilities include template creation, schema/data initialization,
 * final template hardening, and dropping temporary/test databases.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseTemplateService {

    public static final String DB_TEMPLATE_NAME = "integration_template_db";
    private static final String TEMPLATE_DATASOURCE_KEY = "template_init";

    @Value("${spring.datasource.hikari.schema}")
    private String hikariSchemaName;

    private final DataSourceFactory dataSourceFactory;
    private final SqlSupport sqlSupport;
    private final RoutingDataSource routingDataSource;
    private final TestDatabaseSchemaPreparer testDatabaseSchemaPreparer;
    private JdbcClient systemJdbcClient;

    @PostConstruct
    public void initializeSystemJdbcTemplate() {
        DataSource systemDataSource = routingDataSource.getDataSourceByKey(RoutingDataSource.SYSTEM_DB_KEY);
        if (systemDataSource == null) {
            throw new IllegalStateException("System datasource was not found in RoutingDataSource");
        }
        systemJdbcClient = JdbcClient.create(systemDataSource);
    }

    public void createAndPopulateTemplate() {
        log.info("Creating template database: {}", DB_TEMPLATE_NAME);

        createTemplate();
        populateTemplate();
        finalizeTemplate();

        log.info("Template database created and configured");
    }

    public void createFromTemplate(String newDbName) {
        executeSystemSql("CREATE DATABASE " + newDbName + " WITH TEMPLATE " + DB_TEMPLATE_NAME + " STRATEGY FILE_COPY");
    }

    public void dropTemplate() {
        try {
            executeSystemSql("UPDATE pg_database SET datistemplate = FALSE WHERE datname = '" + DB_TEMPLATE_NAME + "'");
            executeSystemSql("DROP DATABASE IF EXISTS " + DB_TEMPLATE_NAME + " WITH (FORCE)");
        } catch (Exception e) {
            log.warn("Failed to drop template database: {}", e.getMessage());
        }
    }

    public void dropDatabase(String dbName) {
        try {
            log.debug("Dropping database: {}", dbName);
            systemJdbcClient.sql("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)").update();
        } catch (Exception e) {
            log.warn("Failed to drop database {}: {}", dbName, e.getMessage());
        }
    }

    private void createTemplate() {
        executeSystemSql("CREATE DATABASE " + DB_TEMPLATE_NAME);
    }

    private void populateTemplate() {
        log.info("Populating template database with schema and data...");

        DataSource templateDataSource = dataSourceFactory.createTemplateDataSource(DB_TEMPLATE_NAME);
        JdbcTemplate templateJdbcTemplate = new JdbcTemplate(templateDataSource);

        templateJdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + hikariSchemaName);
        templateJdbcTemplate.execute("ALTER DATABASE " + DB_TEMPLATE_NAME + " SET search_path TO " + hikariSchemaName + ", public");
        sqlSupport.executeScripts(templateDataSource, "classpath:/db/db-init.sql");
        templateJdbcTemplate.execute("SET search_path TO " + hikariSchemaName + ", public");

        routingDataSource.putDataSource(TEMPLATE_DATASOURCE_KEY, templateDataSource);
        routingDataSource.withCurrentDb(TEMPLATE_DATASOURCE_KEY, () -> {
            log.info("Running schema preparation scripts...");
            testDatabaseSchemaPreparer.prepareSchema();
            log.info("Schema preparation completed successfully");
        });
    }

    private void finalizeTemplate() {
        DataSource templateDataSource = dataSourceFactory.createTemplateDataSource(DB_TEMPLATE_NAME);
        JdbcTemplate templateJdbcTemplate = new JdbcTemplate(templateDataSource);
        templateJdbcTemplate.execute("VACUUM FULL");
        executeSystemSql("CHECKPOINT");

        executeSystemSql("UPDATE pg_database SET datistemplate = TRUE WHERE datname = '" + DB_TEMPLATE_NAME + "'");
        executeSystemSql("REVOKE CONNECT ON DATABASE " + DB_TEMPLATE_NAME + " FROM PUBLIC");
        executeSystemSql("ALTER DATABASE " + DB_TEMPLATE_NAME + " WITH ALLOW_CONNECTIONS FALSE");
        executeSystemSql("UPDATE pg_database SET datistemplate = TRUE WHERE datname = '" + DB_TEMPLATE_NAME + "'");

        executeSystemSql("CREATE DATABASE warmup_db WITH TEMPLATE " + DB_TEMPLATE_NAME + " STRATEGY FILE_COPY");
        executeSystemSql("DROP DATABASE warmup_db");
    }

    private void executeSystemSql(String sql) {
        systemJdbcClient.sql(sql).update();
    }
}
