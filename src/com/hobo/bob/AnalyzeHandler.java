package com.hobo.bob;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
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
import com.hobo.bob.model.Keyword;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.Span;

public class AnalyzeHandler implements RequestHandler<AnalyzeRequest, AnalyzeResponse> {

	private AmazonDynamoDB dynamoDb = null;
	
	private LambdaLogger logger;

	@Override
	public AnalyzeResponse handleRequest(AnalyzeRequest input, Context context) {
		logger = context.getLogger();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		logger.log("input: " + gson.toJson(input));
		AnalyzeResponse response = new AnalyzeResponse();
		try {
			initDynamoDbClient();

			try {
				Set<String> multiwordKeywords = getMultiwordKeywords();
				Set<String> ignoredVerbs = findVerbs(input.getJobsText(), multiwordKeywords);
				Set<String> ignoredLocations = findLocations(input.getJobsText(), multiwordKeywords);
				List<Keyword> missingKeywords = analyze(input.getResumeText(), input.getJobsText(), ignoredVerbs,
						ignoredLocations, multiwordKeywords);

				response.setMissingKeywords(missingKeywords);
				ignoredVerbs.forEach(verb -> {
					response.getIgnoredVerbs().add(WordUtils.capitalizeFully(verb));
				});
				ignoredLocations.forEach(verb -> {
					response.getIgnoredLocations().add(WordUtils.capitalizeFully(verb));
				});
				Collections.sort(response.getIgnoredVerbs());
				Collections.sort(response.getIgnoredLocations());
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

	private Set<String> findVerbs(String jobsText, Set<String> multiwordKeywords) throws IOException {
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
			if (tags[i].startsWith("V") && !token.endsWith("ing") && !multiwordKeywords.contains(token)) {
				ignoredVerbs.add(token);
			}
		}

		return ignoredVerbs;
	}

	private Set<String> findLocations(String jobsText, Set<String> multiwordKeywords) throws IOException {
		if (jobsText == null || jobsText.isEmpty()) {
			throw new IllegalArgumentException("Jobs was empty");
		}

		Set<String> ignoredLocations = new HashSet<>();
		TokenNameFinderModel model = new TokenNameFinderModel(new FileInputStream("en-ner-location.bin"));
		NameFinderME finder = new NameFinderME(model);

		WhitespaceTokenizer whitespaceTokenizer = WhitespaceTokenizer.INSTANCE;
		String[] tokens = whitespaceTokenizer.tokenize(jobsText);
		Span[] spans = finder.find(tokens);
		for (Span span : spans) {
			logger.log("Found location: " + span + "\n" + tokens[span.getStart()] + "\n" + span.getProb());
			String loc = tokens[span.getStart()].toLowerCase();
			if (span.getProb() > 0.75 && !multiwordKeywords.contains(loc)) {
				ignoredLocations.add(loc);
				if (span.getEnd() > span.getStart() + 1) {
					for (int i = span.getStart() + 1; i < span.getEnd(); i++) {
						loc = tokens[i].toLowerCase();
						if (!multiwordKeywords.contains(loc)) {
							ignoredLocations.add(loc);
						}
					}
				}
			}
		}

		return ignoredLocations;
	}

	private List<Keyword> analyze(String resumeText, String jobsText, Set<String> ignoredVerbs,
			Set<String> ignoredLocations, Set<String> multiwordKeywords) {
		if (resumeText == null || resumeText.isEmpty() || jobsText == null || jobsText.isEmpty()) {
			throw new IllegalArgumentException("Resume or jobs were empty");
		}

		Set<String> resumeKeywords = parse(resumeText, multiwordKeywords);
		Set<String> jobKeywords = parse(jobsText, multiwordKeywords);

		jobKeywords.removeAll(resumeKeywords);
		jobKeywords.removeAll(getIgnoredKeywords());

		ignoreSet(jobKeywords, ignoredVerbs);
		ignoreSet(jobKeywords, ignoredLocations);

		List<Keyword> missingKeywords = new ArrayList<>();
		String jobsTextLower = jobsText.toLowerCase();
		for (String keyword : jobKeywords) {
			missingKeywords.add(new Keyword(WordUtils.capitalizeFully(keyword),
					StringUtils.countMatches(jobsTextLower, keyword)));
		}

		Collections.sort(missingKeywords, new Comparator<Keyword>() {

			@Override
			public int compare(Keyword o1, Keyword o2) {
				// Want max instances followed by alphabetical order of keywords
				return o1.getInstances() != o2.getInstances() ? o2.getInstances() - o1.getInstances()
						: o1.getKeyword().compareTo(o2.getKeyword());
			}

		});

		return missingKeywords;
	}

	private Set<String> parse(String toParse, Set<String> multiwordKeywords) {
		toParse = toParse.toLowerCase();
		Set<String> keywords = new HashSet<>();
		Pattern pattern = Pattern.compile("[\\w-]*[a-z][\\w-]*");
		Matcher matcher = pattern.matcher(toParse);
		while (matcher.find()) {
			keywords.add(matcher.group());
		}

		for (String multiword : multiwordKeywords) {
			if (toParse.contains(multiword)) {
				keywords.add(multiword);
			}
		}

		return keywords;
	}

	private void ignoreSet(Set<String> jobKeywords, Set<String> ignored) {
		// Determine only the ignored keywords that would actually have been returned
		// and remove
		ignored.retainAll(jobKeywords);
		jobKeywords.removeAll(ignored);
	}

	private void initDynamoDbClient() {
		if (dynamoDb == null) {
			dynamoDb = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_1)
					.withClientConfiguration(new ClientConfiguration().withRequestTimeout(5000)).build();
		}
	}

	private Set<String> getMultiwordKeywords() {
		return scanTable("MultiwordKeywords");
	}

	private Set<String> getIgnoredKeywords() {
		return scanTable("IgnoredKeywords");
	}

	private Set<String> scanTable(String table) {
		Set<String> keywords = new HashSet<>();
		ScanResult result = dynamoDb.scan(new ScanRequest().withTableName(table));
		for (Map<String, AttributeValue> item : result.getItems()) {
			keywords.add(item.get("keyword").getS());
		}
		return keywords;
	}
}
