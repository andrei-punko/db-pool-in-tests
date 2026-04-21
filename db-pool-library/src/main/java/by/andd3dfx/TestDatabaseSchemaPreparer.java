package by.andd3dfx;

/**
 * Дополнительная подготовка схемы в template-БД после {@code db-init.sql}.
 */
@FunctionalInterface
public interface TestDatabaseSchemaPreparer {

    void prepareSchema();
}
