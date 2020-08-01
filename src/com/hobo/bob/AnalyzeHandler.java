package com.hobo.bob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.WordUtils;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
//import com.google.gson.Gson;
import com.hobo.bob.model.AnalyzeRequest;
import com.hobo.bob.model.AnalyzeResponse;

public class AnalyzeHandler implements RequestHandler<AnalyzeRequest, AnalyzeResponse> {

	private AmazonDynamoDB dynamoDb = null;

	@Override
	public AnalyzeResponse handleRequest(AnalyzeRequest input, Context context) {
		AnalyzeResponse response = new AnalyzeResponse();
		try {
			initDynamoDbClient();
			String user = null;//input.getRequestContext().getIdentity().getUser();

//			Map<String, List<String>> parameters = input.getMultiValueQueryStringParameters();
			try {
//				List<String> missingKeywords = analyze(user, input.getparameters.get("resume"), parameters.get("jobs"));
				List<String> missingKeywords = analyze(user, input.getResumeText(), input.getJobsText());
//				Gson gson = new Gson();
				response.setMissingKeywords(missingKeywords);
				//.setBody(gson.toJson(missingKeywords));
				response.setStatus(200);
				response.setMessage("Success");
			} catch (Exception e) {
				response.setMessage("An error occurred: " + e.getMessage());
				response.setStatus(500);
			}
		} finally {
			if (dynamoDb != null) {
				dynamoDb.shutdown();
				dynamoDb = null;
			}
		}

		return response;
	}

	private List<String> analyze(String user, String resumeText, String jobsText) {
		if (resumeText == null || resumeText.isEmpty() || jobsText == null || jobsText.isEmpty()) {
			throw new IllegalArgumentException("Resume or jobs were empty");
		}

		Set<String> resumeKeywords = parse(user, resumeText);
		Set<String> jobKeywords = parse(user, jobsText);

		jobKeywords.removeAll(resumeKeywords);
		jobKeywords.removeAll(getIgnoredKeywords(user));

		List<String> missingKeywords = new ArrayList<>();
		for (String keyword : jobKeywords) {
			missingKeywords.add(WordUtils.capitalizeFully(keyword));
		}
		Collections.sort(missingKeywords);

		return missingKeywords;
	}

	private Set<String> parse(String user, String toParse) {
		toParse = toParse.toLowerCase();
		Set<String> keywords = new HashSet<>();
		Pattern pattern = Pattern.compile("[\\w-]+");
		Matcher matcher = pattern.matcher(toParse);
		while (matcher.find()) {
			keywords.add(matcher.group());
		}

		List<String> multiwordKeywords = getMultiwordKeywords(user);
		for (String multiword : multiwordKeywords) {
			if (toParse.contains(multiword)) {
				keywords.add(multiword);
			}
		}

		return keywords;
	}

	private void initDynamoDbClient() {
		if (dynamoDb == null) {
			dynamoDb = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_1)
					.withClientConfiguration(new ClientConfiguration().withRequestTimeout(5000)).build();
		}
	}

	private List<String> getMultiwordKeywords(String user) {
		return scanTable(user, "MultiwordKeywords");
	}

	private List<String> getIgnoredKeywords(String user) {
		return scanTable(user, "IgnoredKeywords");
	}
	
	private List<String> scanTable(String user, String table) {
		List<String> keywords = new ArrayList<>();
		ScanResult result = dynamoDb.scan(new ScanRequest().withTableName(table));
		for (Map<String, AttributeValue> item : result.getItems()) {
			keywords.add(item.get("keyword").getS());
		}
		return keywords;
	}
}
