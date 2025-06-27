databaseChangeLog = {
  // From 7-2
  changeSet(author: "mchaib (manual)", id: "20250627-1424-001") {
    preConditions(onFail: 'MARK_RAN') {
      // The precondition checks if a primary key does not exist on the table.
      // The changeSet will only run if this is true.
      not {
        primaryKeyExists(tableName: 'package_trigger_resync_job')
      }
    }

    addPrimaryKey(
      columnNames: "id",
      constraintName: "package_trigger_resync_jobPK",
      tableName: "package_trigger_resync_job"
    )
  }

  // From 5-5
  changeSet(author: "mchaib (manual)", id: "20250627-1424-002") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'push_kb_session')
      }
    }

    addPrimaryKey(
      columnNames: "pkbs_id",
      constraintName: "push_kb_sessionPK",
      tableName: "push_kb_session"
    )
  }

  // From 5-5
  changeSet(author: "mchaib (manual)", id: "20250627-1424-003") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'push_kb_chunk')
      }
    }

    addPrimaryKey(
      columnNames: "pkbc_id",
      constraintName: "push_kb_chunkPK",
      tableName: "push_kb_chunk"
    )
  }

  // From 5-3
  changeSet(author: "mchaib (manual)", id: "20250627-1424-004") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'availability_constraint')
      }
    }

    addPrimaryKey(
      columnNames: "avc_id",
      constraintName: "availability_constraintPK",
      tableName: "availability_constraint"
    )
  }

  // From 5-2
  changeSet(author: "mchaib (manual)", id: "20250627-1424-005") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'alternate_resource_name')
      }
    }

    addPrimaryKey(
      columnNames: "arn_id",
      constraintName: "alternate_resource_namePK",
      tableName: "alternate_resource_name"
    )
  }

  changeSet(author: "mchaib (manual)", id: "20250627-1424-006") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'content_type')
      }
    }

    addPrimaryKey(
      columnNames: "ct_id",
      constraintName: "content_typePK",
      tableName: "content_type"
    )
  }

  changeSet(author: "mchaib (manual)", id: "20250627-1424-007") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'package_description_url')
      }
    }

    addPrimaryKey(
      columnNames: "pdu_id",
      constraintName: "package_description_urlPK",
      tableName: "package_description_url"
    )
  }

  // From 5-1
  changeSet(author: "mchaib (manual)", id: "20250627-1424-009") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'resource_rematch_job')
      }
    }

    addPrimaryKey(
      columnNames: "id",
      constraintName: "resource_rematch_jobPK",
      tableName: "resource_rematch_job"
    )
  }

  // Changes above go back to update-mod-agreements-5-0.groovy
}