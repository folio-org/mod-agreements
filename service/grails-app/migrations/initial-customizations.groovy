databaseChangeLog = {

    // Currently, the default folio environment has this for us, but this is left
    // here as a signpost for users who might be running in a different context
    //
    // https://stackoverflow.com/questions/30368724/enable-postgresql-extensions-in-jdbc
    //
    // changeSet(author: "ianibbo (generated)", id: "1527414162857-0a") {
    //   grailsChange {
    //     change {
    //       sql.execute("create extension pg_trgm WITH SCHEMA ${database.defaultSchemaName}".toString())
    //     }
    //   }
    // }

    changeSet(author: "ianibbo (generated)", id: "1537982176428-1") {
      createIndex(indexName: "id_idx", tableName: "identifier") {
        column(name: "id_value")
        column(name: "id_ns_fk")
      }
    }

    changeSet(author: "ianibbo (generated)", id: "1537982176428-2") {
      grailsChange {
        change {
					
					sql.execute("SET SEARCH_PATH=${database.defaultSchemaName}, public;".toString())
          // grailsChange gives us an sql variable which inherits the current connection, and hence should
          // get the schema
          // sql.execute seems to get a bit confused when passed a GString. Work it out before
          def cmd = "CREATE INDEX res_name_trigram_idx ON ${database.defaultSchemaName}.erm_resource USING GIN (res_name gin_trgm_ops)".toString()
          sql.execute(cmd);
        }
      }
    }

//    changeSet(author: "ianibbo (generated)", id: "1537982176428-3") {
//      grailsChange {
//        change {
//          // grailsChange gives us an sql variable which inherits the current connection, and hence should
//          // get the schema. sql.execute seems to get a bit confused when passed a GString. Work it out before by calling toString
//          def cmd = "CREATE INDEX ti_title_trigram_idx ON ${database.defaultSchemaName}.title_instance USING GIN (ti_title gin_trgm_ops)".toString()
//          sql.execute(cmd);
//        }
//      }
//    }

    changeSet(author: "ianibbo (generated)", id: "1537982176428-4") {
      grailsChange {
        change {
					sql.execute("SET SEARCH_PATH=${database.defaultSchemaName}, public;".toString())
          // grailsChange gives us an sql variable which inherits the current connection, and hence should
          // get the schema. sql.execute seems to get a bit confused when passed a GString. Work it out before by calling toString
          def cmd = "CREATE INDEX work_title_trigram_idx ON ${database.defaultSchemaName}.work USING GIN (w_title gin_trgm_ops)".toString()
          sql.execute(cmd);
        }
      }
    }

//    changeSet(author: "ianibbo (generated)", id: "1537982176428-5") {
//        createTable(tableName: "node") {
//            column(name: "nd_id", type: "VARCHAR(36)") {
//                constraints(nullable: "false")
//            }
//
//            column(name: "nd_version", type: "BIGINT") {
//                constraints(nullable: "false")
//            }
//
//            column(name: "nd_label", type: "VARCHAR(255)")
//
//            column(name: "nd_node_type", type: "VARCHAR(255)")
//
//            column(name: "nd_parent", type: "VARCHAR(36)")
//
//            column(name: "nd_reference_class", type: "VARCHAR(255)")
//
//            column(name: "nd_reference_id", type: "VARCHAR(36)")
//        }
//    }
}
