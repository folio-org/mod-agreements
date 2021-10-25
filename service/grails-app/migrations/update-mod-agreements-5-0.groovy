databaseChangeLog = {
  // add dateCreated and lastUpdated timestamp to SubscriptionAgreement and Entitlement
  changeSet(author: "claudia (manual)", id: "202107201437-001") {
    addColumn (tableName: "subscription_agreement" ) {
      column(name: "sa_date_created", type: "timestamp")
      column(name: "sa_last_updated", type: "timestamp")
    }   
  }

  changeSet(author: "claudia (manual)", id: "202107201437-002") {
    addColumn (tableName: "entitlement" ) {
      column(name: "ent_date_created", type: "timestamp")
      column(name: "ent_last_updated", type: "timestamp")
    }
  }

  changeSet(author: "efreestone (manual)", id: "20210818-1423-001") {
    createTable(tableName: "title_ingest_job") {
      column(name: "id", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }
    }
  }

  changeSet(author: "efreestone (manual)", id: "20210818-1423-002") {
    addPrimaryKey(columnNames: "id", constraintName: "title_ingest_jobPK", tableName: "title_ingest_job")
  }

  changeSet(author: "efreestone (manual)", id: "20210922-1534-001") {
    addColumn (tableName: "erm_resource" ) {
      column(name: "res_normalized_name", type: "VARCHAR(255)")
    }
  }

  changeSet(author: "efreestone (manual)", id: "20210922-1534-002") {
    // Need to normalise name for each existing erm_resource
    grailsChange {
      change {
        sql.eachRow("SELECT id, res_name FROM ${database.defaultSchemaName}.erm_resource".toString()) { def row ->
            sql.execute(
              """
                UPDATE ${database.defaultSchemaName}.erm_resource SET res_normalized_name = :normName WHERE id = :id;
              """.toString(),
              [
                'normName': org.olf.general.StringUtils.normaliseWhitespaceAndCase(row.res_name),
                'id': row.id
              ]
            )
        }
      }
    }
  }
}
