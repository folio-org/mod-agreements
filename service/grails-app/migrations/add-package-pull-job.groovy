databaseChangeLog = {
  changeSet(author: 'mod-agreements', id: '20260923-package-pull-job') {
    createTable(tableName: 'package_pull_job') {
      column(name: 'id', type: 'VARCHAR(36)') {
        constraints(nullable: false, primaryKey: true, primaryKeyName: 'package_pull_jobPK')
      }
      column(name: 'package_id', type: 'VARCHAR(36)') { constraints(nullable: false) }
      column(name: 'remote_kb_id', type: 'VARCHAR(36)') { constraints(nullable: false) }
      column(name: 'source_claimed', type: 'BOOLEAN', defaultValueBoolean: false) { constraints(nullable: false) }
    }
  }
}
