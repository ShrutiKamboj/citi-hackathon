package citi.hackathon.predictor.service.impl;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import citi.hackathon.predictor.service.ClassificationService;
import twitter4j.JSONObject;

@Service
public class ClassificationServiceImpl implements ClassificationService {

	@Autowired RestTemplate restTemplate;
	
	@Value("${dataset.url}")
	String baseUrl;
	
	@Value("${dataset.apikey}")
	String apikey;
	
	@Value("${dataset.classifyEndPoint}")
	String classifyEndPoint;
	
	public static void main(String[] args) throws URISyntaxException, FileNotFoundException {
		ClassificationServiceImpl impl = new ClassificationServiceImpl();
		
	}

	@Override
	public String classifyTweets(String tweetText) throws Exception {
		String url = baseUrl+classifyEndPoint+tweetText;
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", apikey);
		headers.add("Content-Type", "application/x-www-form-urlencoded");
		HttpEntity<String> request = new HttpEntity<String>(headers);
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
		if(!response.getStatusCode().equals(HttpStatus.OK))
		{
			throw new Exception(response.getBody());
		}
		else
		{
			String tweetProcessedCategory = new JSONObject(response.getBody()).getString("top_class");
			System.out.println("approximations: "+new JSONObject(response.getBody()).getString("classes"));
			System.out.println("choosen cat: "+tweetProcessedCategory);
			return tweetProcessedCategory;
		}
	}

}
