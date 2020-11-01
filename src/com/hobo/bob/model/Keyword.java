package com.hobo.bob.model;

public class Keyword {
	private String keyword;
	private int instances;

	public Keyword(String keyword, int instances) {
		this.keyword = keyword;
		this.instances = instances;
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	public int getInstances() {
		return instances;
	}

	public void setInstances(int instances) {
		this.instances = instances;
	}
}
