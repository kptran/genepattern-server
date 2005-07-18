package org.genepattern.server.webservice.server.local;

import org.genepattern.server.webservice.server.Analysis;

import org.genepattern.webservice.*;

/**
 *  local Analysis client
 *
 * @author    Joshua Gould
 */
public class LocalAnalysisClient {
   Analysis service;
   String userName;


   public LocalAnalysisClient(final String userName) {
      this.userName = userName;
      service =
         new Analysis() {
            protected String getUsernameFromContext() {
               return userName;
            }
         };
   }


   public void deleteJob(int jobId) throws WebServiceException {
      service.deleteJob(jobId);
   }


   public void deleteJobResultFile(int jobId, String value) throws WebServiceException {
      service.deleteJobResultFile(jobId, value);
   }
	
      

   public void terminateJob(int jobId) throws WebServiceException {
      service.terminateJob(jobId);
   }


   public JobInfo[] getJobs(String username, int maxJobNumber, int maxEntries, boolean all) throws WebServiceException {
      return service.getJobs(username, maxJobNumber, maxEntries, all);
   }
	
	public JobInfo getJob(int jobId) throws WebServiceException {

		return service.getJob(jobId);
	}

	public String createProvenancePipeline(JobInfo[] jobs, String pipelineName){
	     return service.createProvenancePipeline(jobs, pipelineName);
  		
	}

	public String createProvenancePipeline(String fileUrlOrJobNumber, String pipelineName){
	     return service.createProvenancePipeline(fileUrlOrJobNumber, pipelineName);
  		
	}

	public JobInfo[] findJobsThatCreatedFile(String fileURLOrJobNumber){
	     return service.findJobsThatCreatedFile(fileURLOrJobNumber);
  		
	}
	
	 public JobInfo[] getChildren(int jobNumber) throws WebServiceException {
		int[] children = service.getChildren(jobNumber);
		JobInfo[] childJobs = new JobInfo[children.length];

		for (int i = 0, length = children.length; i < length; i++) {
			childJobs[i] = service.getJob(children[i]);
		}
		return childJobs;
	}



}
