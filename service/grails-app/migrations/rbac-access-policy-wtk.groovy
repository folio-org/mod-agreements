/**
 * Add grails AccessPolicyEntity database migrations
 */
databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "2025-06-18-0935-001") {
    createTable(tableName: "access_policy") {
      column(name: "id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }

      column(name: "version", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "acc_pol_type", type: "VARCHAR(255)")
      column(name: "acc_pol_description", type: "text")
      column(name: "acc_pol_date_created", type: "timestamp")
      column(name: "acc_pol_policy_uuid", type: "VARCHAR(255)")
    }

    addPrimaryKey(columnNames: "id", constraintName: "access_policy_PK", tableName: "access_policy")

    createIndex(indexName: "access_policy_type_idx", tableName: "access_policy") {
      column(name: "acc_pol_type")
    }

    createIndex(indexName: "access_policy_policy_uuid_idx", tableName: "access_policy") {
      column(name: "acc_pol_policy_uuid")
    }

    createIndex(indexName: "access_policy_type_policy_uuid_idx", tableName: "access_policy") {
      column(name: "acc_pol_type")
      column(name: "acc_pol_policy_uuid")
    }

    createIndex(indexName: "access_policy_date_created_idx", tableName: "access_policy") {
      column(name: "acc_pol_date_created")
    }

    // GIN index on access policy description
    grailsChange {
      change {
        def cmd = "CREATE INDEX access_policy_description_idx ON ${database.defaultSchemaName}.access_policy USING gin (acc_pol_description);".toString()
        sql.execute(cmd);
      }
    }
  }
}

