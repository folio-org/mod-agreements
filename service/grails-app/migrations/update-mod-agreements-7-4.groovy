databaseChangeLog = {
  changeSet(author: "mchaib (manual)", id: "20250716-1620-001") {
    // Create a refDataCategory
//    grailsChange {
//      change {
//        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_category (rdc_id, rdc_version, rdc_description) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, 'SubscriptionAgreement.ReasonForClosure' as description WHERE NOT EXISTS (SELECT rdc_description FROM ${database.defaultSchemaName}.refdata_category WHERE (rdc_description)=('SubscriptionAgreement.ReasonForClosure') LIMIT 1);".toString())
//      }
//    }

    // Create the "Missing" refDataValue for LifecycleStatus category
    grailsChange {
      change {
        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_value (rdv_id, rdv_version, rdv_value, rdv_owner, rdv_label) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, 'missingLifecycleStatusRefDataValue' as value, (SELECT rdc_id FROM  ${database.defaultSchemaName}.refdata_category WHERE rdc_description='Pkg.LifecycleStatus') as owner, 'missingLifecycleStatusRefDataValue' as label WHERE NOT EXISTS (SELECT rdv_id FROM ${database.defaultSchemaName}.refdata_value INNER JOIN ${database.defaultSchemaName}.refdata_category ON refdata_value.rdv_owner = refdata_category.rdc_id WHERE rdc_description='Pkg.LifecycleStatus' AND rdv_value='missingLifecycleStatusRefDataValue' LIMIT 1);".toString())
      }
    }


//    sql.execute("UPDATE ${database.defaultSchemaName}.package SET pkg_lifecycle_status_fk='test2'".toString())
    grailsChange {
      change {
        sql.execute("""
          UPDATE ${database.defaultSchemaName}.package
          SET
            pkg_lifecycle_status_fk = (
              SELECT ${database.defaultSchemaName}.refdata_value.rdv_id
              FROM ${database.defaultSchemaName}.refdata_value
              INNER JOIN ${database.defaultSchemaName}.refdata_category ON ${database.defaultSchemaName}.refdata_value.rdv_owner = ${database.defaultSchemaName}.refdata_category.rdc_id
              WHERE ${database.defaultSchemaName}.refdata_category.rdc_description = 'Pkg.LifecycleStatus'
                AND ${database.defaultSchemaName}.refdata_value.rdv_value = 'missingLifecycleStatusRefDataValue'
              LIMIT 1
            )
          WHERE
            NOT EXISTS (
              SELECT 1
              FROM ${database.defaultSchemaName}.refdata_value
              WHERE ${database.defaultSchemaName}.refdata_value.rdv_id = ${database.defaultSchemaName}.package.pkg_lifecycle_status_fk
            )
        """.toString())
      }
    }

    // Create the "Missing" refDataValue for AvailabilityScope category
    grailsChange {
      change {
        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_value (rdv_id, rdv_version, rdv_value, rdv_owner, rdv_label) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, 'missingAvailabilityScopeRefDataValue' as value, (SELECT rdc_id FROM  ${database.defaultSchemaName}.refdata_category WHERE rdc_description='Pkg.AvailabilityScope') as owner, 'missingAvailabilityScopeRefDataValue' as label WHERE NOT EXISTS (SELECT rdv_id FROM ${database.defaultSchemaName}.refdata_value INNER JOIN ${database.defaultSchemaName}.refdata_category ON refdata_value.rdv_owner = refdata_category.rdc_id WHERE rdc_description='Pkg.AvailabilityScope' AND rdv_value='missingAvailabilityScopeRefDataValue' LIMIT 1);".toString())
      }
    }
  }

//  changeSet(author: "mchaib (manual)", id: "20250716-1620-002") {
//    addForeignKeyConstraint(baseColumnNames: "pkg_lifecycle_status_fk", baseTableName: "package", constraintName: "lifecycle_status_to_rdv_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value")
//    addForeignKeyConstraint(baseColumnNames: "pkg_availability_scope_fk", baseTableName: "package", constraintName: "availability_scope_to_rdv_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value")
//  }
}