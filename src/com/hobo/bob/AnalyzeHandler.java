package com.hobo.bob;

import java.io.FileInputStream;
import java.io.IOException;
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
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hobo.bob.model.AnalyzeRequest;
import com.hobo.bob.model.AnalyzeResponse;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.WhitespaceTokenizer;

public class AnalyzeHandler implements RequestHandler<AnalyzeRequest, AnalyzeResponse> {

	private AmazonDynamoDB dynamoDb = null;

	@Override
	public AnalyzeResponse handleRequest(AnalyzeRequest input, Context context) {
		LambdaLogger logger = context.getLogger();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		logger.log("input: " + gson.toJson(input));
		AnalyzeResponse response = new AnalyzeResponse();
		try {
			initDynamoDbClient();

			try {
				Set<String> ignoredVerbs = findVerbs(input.getJobsText());
				List<String> missingKeywords = analyze(input.getResumeText(), input.getJobsText(), ignoredVerbs);

				response.setMissingKeywords(missingKeywords);
				ignoredVerbs.forEach(verb -> {
					response.getIgnoredVerbs().add(WordUtils.capitalizeFully(verb));
				});
				Collections.sort(response.getIgnoredVerbs());
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
	
	private Set<String> findVerbs(String jobsText) throws IOException {
		if (jobsText == null || jobsText.isEmpty()) {
			throw new IllegalArgumentException("Jobs was empty");
		}
		
		Set<String> ignoredVerbs = new HashSet<>();
		POSModel model = new POSModel(new FileInputStream("en-pos-maxent.bin"));
		POSTaggerME tagger = new POSTaggerME(model);
		
		WhitespaceTokenizer whitespaceTokenizer = WhitespaceTokenizer.INSTANCE;
		String[] tokens = whitespaceTokenizer.tokenize(jobsText);
		
		String[] tags = tagger.tag(tokens);
		for (int i = 0; i < tags.length; i++) {
			String token = tokens[i].toLowerCase();
			if (tags[i].startsWith("V") && !token.endsWith("ing")) {
				ignoredVerbs.add(token);
			}
		}
		
		return ignoredVerbs;
	}

	private List<String> analyze(String resumeText, String jobsText, Set<String> ignoredVerbs) {
		if (resumeText == null || resumeText.isEmpty() || jobsText == null || jobsText.isEmpty()) {
			throw new IllegalArgumentException("Resume or jobs were empty");
		}

		Set<String> resumeKeywords = parse(resumeText);
		Set<String> jobKeywords = parse(jobsText);

		jobKeywords.removeAll(resumeKeywords);
		jobKeywords.removeAll(getIgnoredKeywords());
		
		// Determine only the verbs that would actually have been returned and remove
		ignoredVerbs.retainAll(jobKeywords);
		jobKeywords.removeAll(ignoredVerbs);

		List<String> missingKeywords = new ArrayList<>();
		for (String keyword : jobKeywords) {
			missingKeywords.add(WordUtils.capitalizeFully(keyword));
		}
		Collections.sort(missingKeywords);

		return missingKeywords;
	}

	private Set<String> parse(String toParse) {
		toParse = toParse.toLowerCase();
		Set<String> keywords = new HashSet<>();
		Pattern pattern = Pattern.compile("[\\w-]*[a-z][\\w-]*");
		Matcher matcher = pattern.matcher(toParse);
		while (matcher.find()) {
			keywords.add(matcher.group());
		}

		List<String> multiwordKeywords = getMultiwordKeywords();
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

	private List<String> getMultiwordKeywords() {
		return scanTable("MultiwordKeywords");
	}

	private List<String> getIgnoredKeywords() {
		return scanTable("IgnoredKeywords");
	}

	private List<String> scanTable(String table) {
		List<String> keywords = new ArrayList<>();
		ScanResult result = dynamoDb.scan(new ScanRequest().withTableName(table));
		for (Map<String, AttributeValue> item : result.getItems()) {
			keywords.add(item.get("keyword").getS());
		}
		return keywords;
	}
}
