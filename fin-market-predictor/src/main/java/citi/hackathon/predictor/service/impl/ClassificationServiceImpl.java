package citi.hackathon.predictor.service.impl;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import citi.hackathon.predictor.configuration.TwitterConfig;
import citi.hackathon.predictor.model.TweetClassificationResponse;
import citi.hackathon.predictor.service.ClassificationService;
import twitter4j.JSONArray;
import twitter4j.JSONObject;

@Service
public class ClassificationServiceImpl implements ClassificationService {

	@Autowired
	RestTemplate restTemplate;

	@Value("${dataset.url}")
	String baseUrl;

	@Value("${dataset.apikey}")
	String apikey;

	@Value("${dataset.classifyEndPoint}")
	String singleTweetClassifyEndPoint;

	@Value("${dataset.classifyMultiTweetsEndPoint}")
	String classifyMultiTweetsEndPoint;
	
	Map<String,Integer> categoryMap = new HashMap<String, Integer>();

	public static void main(String[] args) throws URISyntaxException, FileNotFoundException {
		ClassificationServiceImpl impl = new ClassificationServiceImpl();

	}

	@Override
	@Async
	public TweetClassificationResponse classifySingleTweet(String tweetText) throws UnsupportedEncodingException, InterruptedException, ExecutionException {
		String encodedtext = URLEncoder.encode(tweetText, StandardCharsets.UTF_8.toString());
		String url = baseUrl + singleTweetClassifyEndPoint + encodedtext;
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", apikey);
		headers.add("Content-Type", "application/x-www-form-urlencoded");
		HttpEntity<String> request = new HttpEntity<String>(headers);
		ResponseEntity<String> response = null;
		
		try {
			response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		if (response != null && !response.getStatusCode().equals(HttpStatus.OK)) {
			System.out.println("Not ok");
			return null;
		} else {
			String tweetProcessedCategory = new JSONObject(response.getBody()).getString("top_class");
			System.out.println("approximations: " + new JSONObject(response.getBody()).getString("classes"));
			System.out.println("choosen cat: " + tweetProcessedCategory);
			TweetClassificationResponse tcResponse = new TweetClassificationResponse();
			tcResponse.setCalculatedCategory(tweetProcessedCategory);
			tcResponse.setTweetText(tweetText);
			return tcResponse;
		}
	}

	@Override
	public List<TweetClassificationResponse> classifyMultipleTweets(List<String> tweets) throws Exception {
		tweets.stream().forEach(a-> System.out.println(a));
		String url = baseUrl + classifyMultiTweetsEndPoint;
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("Authorization", apikey);
		HttpEntity<String> request = new HttpEntity<String>(this.makeJsonForInputTweetsText(tweets), headers);
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
		if (!response.getStatusCode().equals(HttpStatus.OK)) {
			throw new Exception(response.getBody());
		} else {
			JSONArray collection = new JSONObject(response.getBody()).getJSONArray("collection");
			System.out.println("collection: " + collection.toString());
			List<TweetClassificationResponse> list = new ArrayList<>();
			for (int i = 0; i < collection.length(); i++) {
				String currText = collection.getJSONObject(i).getString("text");
				String currCat = collection.getJSONObject(i).getString("top_class");
			//	list.add(TweetClassificationResponse.builder().tweetText(currText).calculatedCategory(currCat).build());
			}
			return list;
		}
	}

	private String makeJsonForInputTweetsText(List<String> tweets) {
		JSONArray jsonArray = new JSONArray();
		tweets.forEach(tweet -> {
			jsonArray.put(new JSONObject().put("text", tweet));
		});
		
		return new JSONObject().put("collection", jsonArray).toString();
	}

	@Override
	@Async
	public Future<HashMap<String, Integer>> getClassification(String text) throws UnsupportedEncodingException, InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		TweetClassificationResponse response = classifySingleTweet(text);
		if (categoryMap.containsKey(response.getCalculatedCategory())) {
			int value = categoryMap.get(response.getCalculatedCategory());
			categoryMap.put(response.getCalculatedCategory(), value +1);
		} else {
			categoryMap.put(response.getCalculatedCategory(), 1);
		}
		return (Future<HashMap<String, Integer>>) categoryMap;
	}

}
