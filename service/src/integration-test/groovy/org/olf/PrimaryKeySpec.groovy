package org.olf;

import grails.testing.mixin.integration.Integration;
import groovy.util.logging.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Set;

@Integration
class PrimaryKeySpec extends BaseSpec {

    DataSource dataSource;

    // A set of tables to ignore during the check.
    private static final Set<String> IGNORED_TABLES = [
            'databasechangelog',
            'databasechangeloglock'
            ].asImmutable();

    // A set of schemas to ignore. For postgres, this prevents checking system tables.
    private static final Set<String> IGNORED_SCHEMAS = [
            'information_schema',
            'pg_catalog'
            ].asImmutable();

    def "all application tables should have a primary key defined"() {
        given: "A list to hold tables that fail the check"
        def tablesWithoutPks = []

        when: "We inspect the database schema for all tables"
        withTenant {
            dataSource.connection.withCloseable { Connection connection ->
                DatabaseMetaData metaData = connection.getMetaData()
                String schema = connection.getSchema() // Gets the current schema

                // Get a list of all tables in the current schema
                metaData.getTables(null, schema, "%", ["TABLE"] as String[]).each { tableRow ->
                    String tableName = tableRow.getString("TABLE_NAME")
                    String tableSchema = tableRow.getString("TABLE_SCHEM")

                    // Skip tables we're ignoring
                    if (tableName.toLowerCase() in IGNORED_TABLES || (tableSchema && tableSchema.toLowerCase() in IGNORED_SCHEMAS)) {
                        return
                    }

                    // For the current table, try to find its primary key.
                    ResultSet pkResultSet = metaData.getPrimaryKeys(null, schema, tableName)

                    // If pkResultSet.next() is false, it means the result set is empty,
                    if (!pkResultSet.next()) {
                        tablesWithoutPks.add(tableName)
                    }

                    pkResultSet.close()
                }
            }
        }

        then: "The list of tables without primary keys should be empty"
        tablesWithoutPks.isEmpty()

        and: "If the test fails, an error message is shown"
        if (!tablesWithoutPks.isEmpty()) {
            // Spock will fail at the `then` block and print this information.
            fail "The following application tables are missing a primary key: ${tablesWithoutPks.join(', ')}"
        }
    }
}