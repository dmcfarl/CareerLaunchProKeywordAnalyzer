package com.hobo.bob;

//import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.hobo.bob.model.Category;
import com.hobo.bob.model.Keyword;

//import opennlp.tools.namefind.NameFinderME;
//import opennlp.tools.namefind.TokenNameFinderModel;
//import opennlp.tools.postag.POSModel;
//import opennlp.tools.postag.POSTaggerME;
//import opennlp.tools.tokenize.WhitespaceTokenizer;
//import opennlp.tools.util.Span;

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
				if (input.getResumeText() == null) {
					input.setResumeText("");
				}
					
				if (input.getJobsText() == null || input.getJobsText().isEmpty()) {
					throw new IllegalArgumentException("Job Description is required to extract keywords");
				}
				Map<String, Set<String>> skills = getSkills();
				extractUncategorized(input.getResumeText(), input.getJobsText(), skills);
				response = analyze(input.getResumeText(), input.getJobsText(), skills);

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
		logger.log("output: " + gson.toJson(response));

		return response;
	}
	
	private void extractUncategorized(String resumeText, String jobsText, Map<String, Set<String>> skills) throws IOException {
		String text = resumeText + jobsText;
		Set<String> uncategorizedSkills = skills.get(Category.uncategorized.toString());
		
//		Set<String> ignoredVerbs = findVerbs(text);
//		Set<String> ignoredLocations = findLocations(text);

		Set<String> keywords = parse(text);
		keywords.removeAll(skills.get(Category.ignored.toString()));
		keywords.removeAll(skills.get(Category.hard.toString()));
		keywords.removeAll(skills.get(Category.soft.toString()));

//		ignoreSet(keywords, ignoredVerbs);
//		ignoreSet(keywords, ignoredLocations);
		
//		skills.put(Category.ignoredVerbs.toString(), ignoredVerbs);
//		skills.put(Category.ignoredLocations.toString(), ignoredLocations);
		uncategorizedSkills.addAll(keywords);
	}

//	private Set<String> findVerbs(String text) throws IOException {
//		Set<String> ignoredVerbs = new HashSet<>();
//		POSModel model = new POSModel(new FileInputStream("en-pos-maxent.bin"));
//		POSTaggerME tagger = new POSTaggerME(model);
//
//		WhitespaceTokenizer whitespaceTokenizer = WhitespaceTokenizer.INSTANCE;
//		String[] tokens = whitespaceTokenizer.tokenize(text);
//
//		String[] tags = tagger.tag(tokens);
//		for (int i = 0; i < tags.length; i++) {
//			String token = tokens[i].toLowerCase();
//			if (tags[i].startsWith("V") && !token.endsWith("ing")) {
//				ignoredVerbs.add(token);
//			}
//		}
//
//		return ignoredVerbs;
//	}

//	private Set<String> findLocations(String text) throws IOException {
//		Set<String> ignoredLocations = new HashSet<>();
//		TokenNameFinderModel model = new TokenNameFinderModel(new FileInputStream("en-ner-location.bin"));
//		NameFinderME finder = new NameFinderME(model);
//
//		WhitespaceTokenizer whitespaceTokenizer = WhitespaceTokenizer.INSTANCE;
//		String[] tokens = whitespaceTokenizer.tokenize(text);
//		Span[] spans = finder.find(tokens);
//		for (Span span : spans) {
//			//logger.log("Found location: " + span + "\n" + tokens[span.getStart()] + "\n" + span.getProb());
//			String loc = tokens[span.getStart()].toLowerCase();
//			if (span.getProb() > 0.75) {
//				ignoredLocations.add(loc);
//				if (span.getEnd() > span.getStart() + 1) {
//					for (int i = span.getStart() + 1; i < span.getEnd(); i++) {
//						loc = tokens[i].toLowerCase();
//						ignoredLocations.add(loc);
//					}
//				}
//			}
//		}
//
//		return ignoredLocations;
//	}

	private AnalyzeResponse analyze(String resumeText, String jobsText, Map<String, Set<String>> skills) {
		AnalyzeResponse response = new AnalyzeResponse();
		
		// Set Ignored
//		response.getIgnoredVerbs().addAll(skills.get(Category.ignoredVerbs.toString()));
//		Collections.sort(response.getIgnoredVerbs());
//		response.getIgnoredLocations().addAll(skills.get(Category.ignoredLocations.toString()));
//		Collections.sort(response.getIgnoredLocations());
		
		countSkills(resumeText, jobsText, skills.get(Category.hard.toString()), response.getHardSkills());
		countSkills(resumeText, jobsText, skills.get(Category.soft.toString()), response.getSoftSkills());
		countSkills(resumeText, jobsText, skills.get(Category.uncategorized.toString()), response.getUncategorizedSkills());

		Comparator<Keyword> comparator = new Comparator<Keyword>() {

			@Override
			public int compare(Keyword o1, Keyword o2) {
				// Want max instances followed by alphabetical order of keywords
				return o1.getJobInstances() != o2.getJobInstances() ? o2.getJobInstances() - o1.getJobInstances()
						: o1.getKeyword().compareTo(o2.getKeyword());
			}

		};

		Collections.sort(response.getHardSkills(), comparator);
		Collections.sort(response.getSoftSkills(), comparator);
		Collections.sort(response.getUncategorizedSkills(), comparator);

		return response;
	}

	private Set<String> parse(String toParse) {
		toParse = toParse.toLowerCase();
		Set<String> keywords = new HashSet<>();
		Pattern pattern = Pattern.compile("[\\w-]*[a-z][\\w-]*");
		Matcher matcher = pattern.matcher(toParse);
		while (matcher.find()) {
			keywords.add(matcher.group());
		}

		return keywords;
	}
	
	/**
	 * @deprecated
	 * @return
	 */
	@SuppressWarnings("unused")
	private Set<String> extractMultiword(Set<String> uncategorizedSkills, String toParse) {
		Set<String> keywords = new HashSet<>();
		for (String multiword : uncategorizedSkills) {
			if (toParse.contains(multiword)
					|| (multiword.contains("-") && toParse.contains(multiword.replaceAll("-", " ")))) {
				keywords.add(multiword);
			} else if (multiword.matches(".*\\(.*\\).*") && multiword.indexOf(" (") > 0
					&& multiword.indexOf("(") + 1 < multiword.indexOf(")")) {
				String name = multiword.substring(0, multiword.indexOf(" ("));
				String abbreviation = multiword.substring(multiword.indexOf("(") + 1, multiword.indexOf(")"));
				if (toParse.contains(name) || toParse.matches("(?s).*\\b" + abbreviation + "\\b.*")) {
					keywords.add(multiword);
				}
			}
		}
		return keywords;
	}
	
	private void countSkills(String resumeText, String jobsText, Set<String> skills, List<Keyword> response) {
		for (String skill : skills) {
			Keyword keyword = new Keyword(skill);
			keyword.setJobInstances(countMatches(jobsText, keyword, true));
			if (keyword.getJobInstances() > 0) {
				keyword.setResumeInstances(countMatches(resumeText, keyword, false));
				response.add(keyword);
			}
		}
	}

	private int countMatches(String text, Keyword keyword, boolean setFirstMatch) {
		int total = 0;
		Pattern p = Pattern.compile("\\b" + Pattern.quote(keyword.getKeyword()).replaceAll(" ", "[- \\/]?") + "\\b",
				Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(text);
		if (m.find()) {
			total++;
			if (setFirstMatch) {
				keyword.setKeyword(m.group());
			}
			while (m.find()) {
				total++;
			}
		}

		return total;
	}

	@SuppressWarnings("unused")
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

	private Map<String, Set<String>> getSkills() {
		Map<String, Set<String>> skills = new HashMap<>();
		ScanResult result = dynamoDb.scan(new ScanRequest().withTableName("Skills"));
		for (Map<String, AttributeValue> item : result.getItems()) {
			String category = item.get("category").getS();
			Set<String> skillSet = skills.get(category);
			if (skillSet == null) {
				skillSet = new HashSet<>();
				skills.put(category, skillSet);
			}
			skillSet.add(item.get("keyword").getS());
		}
		
		return skills;
	}
}
