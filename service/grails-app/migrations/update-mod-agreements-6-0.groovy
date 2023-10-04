databaseChangeLog = {
  // Move work over to extend ErmTitleList
  changeSet(author: "efreestone (manual)", id: "20230830-1728-001") {
    // Copy all work ID/versions into title list table
    grailsChange {
      change {
        sql.execute("""
          INSERT INTO ${database.defaultSchemaName}.erm_title_list(id, version)
          SELECT w_id, w_version FROM ${database.defaultSchemaName}.work;
        """.toString())
      }
    }
    
    // We don't seem to drop the id columns for SubAgreement or ErmResource so haven't dropped here either
    dropColumn(columnName: "w_version", tableName: "work")
  }

  // Move IdentifierOccurrence foreign key constraint up to parent table
  // ErmResource and ErmTitleList should share IDs so no need to repopulate database(?)
  changeSet(author: "efreestone (manual)", id: "20230830-1728-002") {
    dropForeignKeyConstraint(
      baseTableName: "identifier_occurrence",
      constraintName: "identifier_occurrence_resource_FK",
    )

    addForeignKeyConstraint(
      baseColumnNames: "io_res_fk",
      baseTableName: "identifier_occurrence",
      constraintName: "identifier_occurrence_resource_FK",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "id",
      referencedTableName: "erm_title_list"
    )
  }
	
	changeSet(author: "sosguthorpe (manual)", id: "20231004-001") {
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX arn_name_idx ON ${database.defaultSchemaName}.alternate_resource_name USING gin (arn_name);".toString()
				sql.execute(cmd);
			}
		}
		grailsChange {
			change {
				def cmd = "CREATE INDEX refdata_category_rdc_description_idx ON ${database.defaultSchemaName}.refdata_category USING gin (rdc_description);".toString()
				sql.execute(cmd);
			}
		}
		grailsChange {
			change {
				def cmd = "CREATE INDEX identifier_id_value_idx ON ${database.defaultSchemaName}.identifier USING gin (id_value);".toString()
				sql.execute(cmd);
			}
		}
		grailsChange {
			change {
				def cmd = "CREATE INDEX erm_resource_res_description_idx ON ${database.defaultSchemaName}.erm_resource USING gin (res_description);".toString()
				sql.execute(cmd);
			}
		}
		
		createIndex(indexName: "arn_owner_idx", tableName: "alternate_resource_name") {
			column(name: "arn_owner_fk")
		}
		createIndex(indexName: "identifier_occurrence_io_res_fk_io_identifier_fk_idx", tableName: "identifier_occurrence") {
			column(name: "io_res_fk")
			column(name: "io_identifier_fk")
		}
		createIndex(indexName: "pkg_availability_scope", tableName: "package") {
			column(name: "pkg_availability_scope_fk")
		}
		
		createIndex(indexName: "pkg_lifecycle_status_idx", tableName: "package") {
			column(name: "pkg_lifecycle_status_fk")
		}
		createIndex(indexName: "res_normalized_name_idx", tableName: "erm_resource") {
			column(name: "res_normalized_name")
		}
		createIndex(indexName: "res_publication_type_idx", tableName: "erm_resource") {
			column(name: "res_publication_type_fk")
		}
		createIndex(indexName: "res_sub_type_idx", tableName: "erm_resource") {
			column(name: "res_sub_type_fk")
		}
		createIndex(indexName: "res_type_idx", tableName: "erm_resource") {
			column(name: "res_type_fk")
		}
		createIndex(indexName: "title_instance_ti_work_fk_idx", tableName: "title_instance") {
			column(name: "ti_work_fk")
		}
	}
}