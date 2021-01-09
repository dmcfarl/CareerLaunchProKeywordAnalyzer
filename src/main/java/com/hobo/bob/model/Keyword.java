package com.hobo.bob.model;

public class Keyword {
	private String keyword;
	private int jobInstances;
	private int resumeInstances;

	public Keyword(String keyword) {
		this.keyword = keyword;
		this.jobInstances = 0;
		this.resumeInstances = 0;
	}

	public Keyword(String keyword, int jobInstances, int resumeInstances) {
		this.keyword = keyword;
		this.jobInstances = jobInstances;
		this.resumeInstances = resumeInstances;
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	public int getJobInstances() {
		return jobInstances;
	}

	public void setJobInstances(int jobInstances) {
		this.jobInstances = jobInstances;
	}

	public int getResumeInstances() {
		return resumeInstances;
	}

	public void setResumeInstances(int resumeInstances) {
		this.resumeInstances = resumeInstances;
	}
}
