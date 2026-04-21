package by.andd3dfx;

/**
 * Optional schema preparation step for the template database after {@code db-init.sql}.
 */
@FunctionalInterface
public interface TestDatabaseSchemaPreparer {

    void prepareSchema();
}
