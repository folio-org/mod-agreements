package org.olf;

import grails.testing.mixin.integration.Integration;
import groovy.util.logging.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Set;

@Integration
@Slf4j
class PrimaryKeySpec extends BaseSpec {

    DataSource dataSource;

    // A set of tables to ignore during the check.
    private static final Set<String> IGNORED_TABLES = [
            'databasechangelog',
            'databasechangeloglock',
            'custom_property_multi_blob_value', // "custom_property" tables are found in  WTK
            'custom_property_multi_decimal_value',
            'custom_property_multi_integer_value',
            'custom_property_multi_local_date_value',
            'custom_property_multi_refdata_refdata_value',
            'custom_property_multi_text_value',
            'log_entry_additional_info',
            'string_template_scopes',  // lookup/mapping table.
            'subscription_agreement_ext_lic_doc',
            'subscription_agreement_supp_doc',
            'tenant_changelog'
            ].asImmutable();

    // A set of schemas to ignore. For postgres, this prevents checking system tables.
    private static final Set<String> IGNORED_SCHEMAS = [
            'information_schema',
            'pg_catalog'
            ].asImmutable();

    def "all application tables should have a primary key defined"() {
        given: "A list to hold tables that fail the check"
        def tablesWithoutPks = []
        def tablesWithPks = []

        when: "We inspect the database schema for all tables"
        withTenant {
            dataSource.connection.withCloseable { Connection connection ->
                DatabaseMetaData metaData = connection.getMetaData()
                String schema = "primarykeyspec_mod_agreements" // Not sure how we autogenerate this schema name for tests. Will have to manually
                log.info("Schema: {}", schema.toString());
                metaData.getTables(null, schema, "%", ["TABLE"] as String[]).each { ResultSet table ->
                    while(table.next()) {
                        String tableName = table.getString("TABLE_NAME");
                        String tableSchema = table.getString("TABLE_SCHEM");

                        if (tableName.toLowerCase() in IGNORED_TABLES || (tableSchema && tableSchema.toLowerCase() in IGNORED_SCHEMAS)) {
                            continue
                        }


                        ResultSet pkResultSet = metaData.getPrimaryKeys(null, schema, tableName);

                        if (!pkResultSet.next()) {
                            // If the primary key resultSet is empty, then that table is missing a PK
                            tablesWithoutPks.add(tableName)
                        } else {
                            tablesWithPks.add(tableName)
                        }

                        pkResultSet.close();
                    }
                }
            }
        }

        then: "The list of tables without primary keys should be empty"
            log.debug("Tables found with PKs: {}", tablesWithPks.toListString());
            log.debug("Tables found missing PKs: {}", tablesWithoutPks.toListString());
            tablesWithoutPks.isEmpty() // Excepting the tables we ignore, there shouldn't be any tables missing PKs
    }
}