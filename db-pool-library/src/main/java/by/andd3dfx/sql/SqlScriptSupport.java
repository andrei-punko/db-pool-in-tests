package by.andd3dfx.sql;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class SqlScriptSupport {

    @Autowired
    private DataSource defaultDataSource;
    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    public void executeScripts(String... resourceNames) {
        executeScripts(defaultDataSource, resourceNames);
    }

    public void executeScripts(DataSource dataSource, String... resourceNames) {
        executeScriptsWithSeparator(dataSource, ScriptUtils.DEFAULT_STATEMENT_SEPARATOR, resourceNames);
    }

    public void executeScriptsAsSingleStatement(String... resourceNames) {
        executeScriptsWithSeparator(defaultDataSource, ScriptUtils.EOF_STATEMENT_SEPARATOR, resourceNames);
    }

    public void executeScriptsWithSeparator(DataSource dataSource, String separator, String... resourceNames) {
        Resource[] resources = Arrays.stream(resourceNames)
                .flatMap(name -> getResource(separator, name))
                .toArray(Resource[]::new);
        try {
            ResourceDatabasePopulator databasePopulator = new ResourceDatabasePopulator(resources);
            databasePopulator.setSeparator(separator);
            databasePopulator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
            databasePopulator.execute(dataSource);
        } catch (ScriptStatementFailedException e) {
            throw new RuntimeException("Schema preparation failed: " + StringUtils.truncate(e.getMessage(), 500));
        }
    }

    private Stream<Resource> getResource(String separator, String name) {
        try {
            if (name.contains("*")) {
                Resource[] foundResources = resourcePatternResolver.getResources(name);
                Resource combinedResource = combineSqlResourcesToSingleResource(Arrays.asList(foundResources), separator);
                return Stream.of(combinedResource);
            }
            return Stream.of(resourcePatternResolver.getResource(name));
        } catch (Exception e) {
            throw new RuntimeException("Error resolving resource: " + name, e);
        }
    }

    private Resource combineSqlResourcesToSingleResource(List<Resource> resources, String separator) {
        resources.sort(Comparator.comparing(r -> {
            try {
                return r.getURL().toString();
            } catch (Exception e) {
                return "";
            }
        }));

        StringBuilder combinedSql = new StringBuilder();
        for (Resource resource : resources) {
            if (resource.isReadable()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        combinedSql.append(line).append("\n");
                    }

                    if (!ScriptUtils.EOF_STATEMENT_SEPARATOR.equals(separator)) {
                        combinedSql.append(separator).append("\n");
                    } else {
                        combinedSql.append("\n");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        final String combinedContent = combinedSql.toString();
        return new InputStreamResource(
                new ByteArrayInputStream(combinedContent.getBytes(StandardCharsets.UTF_8))
        ) {
        };
    }

    public void executeSqlAsSingleStatement(String sql, String prefix, String suffix) {
        DatabasePopulatorUtils.execute(connection -> {
            final PreparedStatement preparedStatement = connection.prepareStatement(prefix + " " + sql + " " + suffix);
            try (preparedStatement) {
                preparedStatement.execute();
            }
        }, defaultDataSource);
    }
}
