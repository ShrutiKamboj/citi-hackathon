package citi.hackathon.predictor.service.impl;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

	public static void main(String[] args) throws URISyntaxException, FileNotFoundException {
		ClassificationServiceImpl impl = new ClassificationServiceImpl();

	}

	@Override
	public TweetClassificationResponse classifySingleTweet(String tweetText) throws Exception {
		String url = baseUrl + singleTweetClassifyEndPoint + tweetText;
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", apikey);
		headers.add("Content-Type", "application/x-www-form-urlencoded");
		HttpEntity<String> request = new HttpEntity<String>(headers);
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
		if (!response.getStatusCode().equals(HttpStatus.OK)) {
			throw new Exception(response.getBody());
		} else {
			String tweetProcessedCategory = new JSONObject(response.getBody()).getString("top_class");
			System.out.println("approximations: " + new JSONObject(response.getBody()).getString("classes"));
			System.out.println("choosen cat: " + tweetProcessedCategory);
			return TweetClassificationResponse
					.builder()
					.tweetText(tweetText)
					.calculatedCategory(tweetProcessedCategory)
					.build();
		}
	}

	@Override
	public List<TweetClassificationResponse> classifyMultipleTweets(List<String> tweets) throws Exception {
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
				list.add(TweetClassificationResponse.builder().tweetText(currText).calculatedCategory(currCat).build());
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

}
