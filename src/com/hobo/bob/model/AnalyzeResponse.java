package com.hobo.bob.model;

import java.util.List;

public class AnalyzeResponse {
	private List<String> missingKeywords;
	private int status;
	private String message;

	public List<String> getMissingKeywords() {
		return missingKeywords;
	}

	public void setMissingKeywords(List<String> missingKeywords) {
		this.missingKeywords = missingKeywords;
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
}
