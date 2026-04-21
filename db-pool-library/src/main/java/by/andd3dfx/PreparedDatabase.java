package by.andd3dfx;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.Objects;

/**
 * A prepared database name or an error produced during preparation.
 */
record PreparedDatabase(String databaseName, Throwable error) {

    public boolean isSuccess() {
        return databaseName != null && error == null;
    }

    public String getRequiredDatabaseName() {
        if (error != null) {
            throw new IllegalStateException("PreparedDatabase is in error state: " + error);
        }
        return Objects.requireNonNull(databaseName, "databaseName");
    }

    public void checkForError() {
        if (error != null) {
            throw ExceptionUtils.<RuntimeException>rethrow(error);
        }
    }

    public static PreparedDatabase success(String databaseName) {
        return new PreparedDatabase(databaseName, null);
    }

    public static PreparedDatabase error(Throwable error) {
        return new PreparedDatabase(null, error);
    }
}
