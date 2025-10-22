databaseChangeLog = {
  // EXAMPLE: Replacing any missing refdataValue values where FK constraints were erroneously not present
  // See: ERM-3765
  changeSet(author: "CalamityC (manual)", id: "20251022-1400-001") {
    // create the Pkg.LifecycleStatus category if it doesn't already exist
    grailsChange {
      change {
        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_category (rdc_id, rdc_version, rdc_description, internal) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, 'Pkg.LifecycleStatus' as description, false as internal WHERE NOT EXISTS (SELECT rdc_description FROM ${database.defaultSchemaName}.refdata_category WHERE (rdc_description)=('Pkg.LifecycleStatus') LIMIT 1);".toString())
      }
    }

    // Create the "missingLifecycleStatusRefDataValue" refDataValue ONLY if needed
    grailsChange {
      change {
        sql.execute("""
          INSERT INTO ${database.defaultSchemaName}.refdata_value (rdv_id, rdv_version, rdv_value, rdv_owner, rdv_label)
          SELECT md5(random()::text || clock_timestamp()::text) AS id,
                0 AS version,
                'missingLifecycleStatusRefDataValue' AS value,
                (SELECT rdc_id
                  FROM ${database.defaultSchemaName}.refdata_category
                  WHERE rdc_description = 'Pkg.LifecycleStatus'
                  LIMIT 1) AS owner,
                'missingLifecycleStatusRefDataValue' AS label
          WHERE
            -- don't recreate if it already exists
            NOT EXISTS (
              SELECT 1
                FROM ${database.defaultSchemaName}.refdata_value rv
                JOIN ${database.defaultSchemaName}.refdata_category rc
                  ON rv.rdv_owner = rc.rdc_id
                WHERE rc.rdc_description = 'Pkg.LifecycleStatus'
                AND rv.rdv_value = 'missingLifecycleStatusRefDataValue'
            )
            AND
            -- create only if there are orphaned lifecycle_status FKs
            EXISTS (
              SELECT 1
                FROM ${database.defaultSchemaName}."package" p
                WHERE p.pkg_lifecycle_status_fk IS NOT NULL
                AND NOT EXISTS (
                      SELECT 1
                        FROM ${database.defaultSchemaName}.refdata_value rv2
                        WHERE rv2.rdv_id = p.pkg_lifecycle_status_fk
                    )
            );
        """.toString())
      }
    }

    /*
    Get the newly created 'missingLifecycleStatusRefDataValue' ID from the refDataValue table and set the pkg_lifecycle_status_fk
    column to that ID where the value currently in the pkg_lifecycle_status_fk DOES NOT CURRENTLY EXIST in the refDataValue table (where block below).
    */
    grailsChange {
      change {
        sql.execute("""
          UPDATE ${database.defaultSchemaName}."package" p
            SET pkg_lifecycle_status_fk = (
                  SELECT rv.rdv_id
                    FROM ${database.defaultSchemaName}.refdata_value rv
                    JOIN ${database.defaultSchemaName}.refdata_category rc
                      ON rv.rdv_owner = rc.rdc_id
                    WHERE rc.rdc_description = 'Pkg.LifecycleStatus'
                      AND rv.rdv_value = 'missingLifecycleStatusRefDataValue'
                    LIMIT 1
                )
          WHERE
            -- only touch rows whose current FK doesn't exist in refdata_value
            NOT EXISTS (
              SELECT 1
                FROM ${database.defaultSchemaName}.refdata_value rvx
                WHERE rvx.rdv_id = p.pkg_lifecycle_status_fk
            )
            AND
            -- only do this if the placeholder actually exists
            EXISTS (
              SELECT 1
                FROM ${database.defaultSchemaName}.refdata_value rv
                JOIN ${database.defaultSchemaName}.refdata_category rc
                  ON rv.rdv_owner = rc.rdc_id
                WHERE rc.rdc_description = 'Pkg.LifecycleStatus'
                  AND rv.rdv_value = 'missingLifecycleStatusRefDataValue'
            );
        """.toString())
      }
    }

    // Create the foreign key constraints on these columns so when a refDataValue is in use by a package, the refDataValue can't be deleted.
    addForeignKeyConstraint(baseColumnNames: "pkg_lifecycle_status_fk", baseTableName: "package", constraintName: "lifecycle_status_to_rdv_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value")
  }

  changeSet(author: "CalamityC (manual)", id: "20251022-1400-002") {
    // create the Pkg.AvailabilityScope category if it doesn't already exist
    grailsChange {
      change {
        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_category (rdc_id, rdc_version, rdc_description, internal) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, 'Pkg.AvailabilityScope' as description, false as internal WHERE NOT EXISTS (SELECT rdc_description FROM ${database.defaultSchemaName}.refdata_category WHERE (rdc_description)=('Pkg.AvailabilityScope') LIMIT 1);".toString())
      }
    }

    // Create the "missingAvailabilityScopeRefDataValue" ONLY if needed
    grailsChange {
      change {
        sql.execute("""
          INSERT INTO ${database.defaultSchemaName}.refdata_value (rdv_id, rdv_version, rdv_value, rdv_owner, rdv_label)
          SELECT md5(random()::text || clock_timestamp()::text) AS id,
                0 AS version,
                'missingAvailabilityScopeRefDataValue' AS value,
                (SELECT rdc_id
                    FROM ${database.defaultSchemaName}.refdata_category
                  WHERE rdc_description = 'Pkg.AvailabilityScope'
                  LIMIT 1) AS owner,
                'missingAvailabilityScopeRefDataValue' AS label
          WHERE
            NOT EXISTS (
              SELECT 1
                FROM ${database.defaultSchemaName}.refdata_value rv
                JOIN ${database.defaultSchemaName}.refdata_category rc
                  ON rv.rdv_owner = rc.rdc_id
              WHERE rc.rdc_description = 'Pkg.AvailabilityScope'
                AND rv.rdv_value = 'missingAvailabilityScopeRefDataValue'
            )
            AND
            EXISTS (
              SELECT 1
                FROM ${database.defaultSchemaName}."package" p
              WHERE p.pkg_availability_scope_fk IS NOT NULL
                AND NOT EXISTS (
                      SELECT 1
                        FROM ${database.defaultSchemaName}.refdata_value rv2
                        WHERE rv2.rdv_id = p.pkg_availability_scope_fk
                    )
            );
        """.toString())
      }
    }

    /*
    Get the newly created 'missingAvailabilityScopeRefDataValue' ID from the refDataValue table and set the pkg_availability_scope_fk
    column to that ID where the value currently in the pkg_availability_scope_fk DOES NOT CURRENTLY EXIST in the refDataValue table (where block below).
    */
    grailsChange {
      change {
        sql.execute("""
          UPDATE ${database.defaultSchemaName}."package" p
            SET pkg_availability_scope_fk = (
                  SELECT rv.rdv_id
                    FROM ${database.defaultSchemaName}.refdata_value rv
                    JOIN ${database.defaultSchemaName}.refdata_category rc
                      ON rv.rdv_owner = rc.rdc_id
                    WHERE rc.rdc_description = 'Pkg.AvailabilityScope'
                      AND rv.rdv_value = 'missingAvailabilityScopeRefDataValue'
                    LIMIT 1
                )
          WHERE
            NOT EXISTS (
              SELECT 1
                FROM ${database.defaultSchemaName}.refdata_value rvx
                WHERE rvx.rdv_id = p.pkg_availability_scope_fk
            )
            AND
            EXISTS (
              SELECT 1
                FROM ${database.defaultSchemaName}.refdata_value rv
                JOIN ${database.defaultSchemaName}.refdata_category rc
                  ON rv.rdv_owner = rc.rdc_id
                WHERE rc.rdc_description = 'Pkg.AvailabilityScope'
                  AND rv.rdv_value = 'missingAvailabilityScopeRefDataValue'
            );
        """.toString())
      }
    }

    // Create the foreign key constraints on these columns so when a refDataValue is in use by a package, the refDataValue can't be deleted.
    addForeignKeyConstraint(baseColumnNames: "pkg_availability_scope_fk", baseTableName: "package", constraintName: "availability_scope_to_rdv_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value")
  }
}
