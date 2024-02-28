package org.olf.general.jobs;

import org.olf.general.jobs.PersistentJob.Type

public class JobRunnerEntry {
	String jobId
	String tenantId
	Type type
	
	public static JobRunnerEntry of ( type, jobId, tenantId ) {
		JobRunnerEntry entry = new JobRunnerEntry()
		entry.jobId = jobId
		entry.type = type
		entry.tenantId = tenantId
		
		entry
	}
}
