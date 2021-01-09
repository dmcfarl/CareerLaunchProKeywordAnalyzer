package com.hobo.bob.model;

import java.util.ArrayList;
import java.util.List;

public class AnalyzeResponse {
	private List<Keyword> hardSkills;
	private List<Keyword> softSkills;
	private List<Keyword> uncategorizedSkills;
//	private List<String> ignoredVerbs;
//	private List<String> ignoredLocations;
	private int status;
	private String message;

	public List<Keyword> getHardSkills() {
		if (hardSkills == null) {
			hardSkills = new ArrayList<>();
		}
		return hardSkills;
	}

	public List<Keyword> getSoftSkills() {
		if (softSkills == null) {
			softSkills = new ArrayList<>();
		}
		return softSkills;
	}

	public List<Keyword> getUncategorizedSkills() {
		if (uncategorizedSkills == null) {
			uncategorizedSkills = new ArrayList<>();
		}
		return uncategorizedSkills;
	}

//	public List<String> getIgnoredVerbs() {
//		if (ignoredVerbs == null) {
//			ignoredVerbs = new ArrayList<>();
//		}
//		return ignoredVerbs;
//	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

//	public List<String> getIgnoredLocations() {
//		if (ignoredLocations == null) {
//			ignoredLocations = new ArrayList<>();
//		}
//		return ignoredLocations;
//	}
}
