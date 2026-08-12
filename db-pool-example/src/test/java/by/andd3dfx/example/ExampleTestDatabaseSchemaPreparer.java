package by.andd3dfx.example;

import by.andd3dfx.config.TestDatabaseSchemaPreparer;
import by.andd3dfx.sql.SqlSupport;

public class ExampleTestDatabaseSchemaPreparer implements TestDatabaseSchemaPreparer {

    private final SqlSupport sqlSupport;

    public ExampleTestDatabaseSchemaPreparer(SqlSupport sqlSupport) {
        this.sqlSupport = sqlSupport;
    }

    @Override
    public void prepareSchema() {
        sqlSupport.executeScripts(
                "classpath:/db/schema.sql",
                "classpath:/db/seed-data.sql"
        );
    }
}
