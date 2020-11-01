package com.hobo.bob.model;

import java.util.ArrayList;
import java.util.List;

public class AnalyzeResponse {
	private List<Keyword> missingKeywords;
	private List<String> ignoredVerbs;
	private List<String> ignoredLocations;
	private int status;
	private String message;

	public List<Keyword> getMissingKeywords() {
		if (missingKeywords == null) {
			missingKeywords = new ArrayList<>();
		}
		return missingKeywords;
	}

	public void setMissingKeywords(List<Keyword> missingKeywords) {
		this.missingKeywords = missingKeywords;
	}

	public List<String> getIgnoredVerbs() {
		if (ignoredVerbs == null) {
			ignoredVerbs = new ArrayList<>();
		}
		return ignoredVerbs;
	}

	public void setIgnoredVerbs(List<String> ignoredVerbs) {
		this.ignoredVerbs = ignoredVerbs;
	}

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

	public List<String> getIgnoredLocations() {
		if (ignoredLocations == null) {
			ignoredLocations = new ArrayList<>();
		}
		return ignoredLocations;
	}
}
