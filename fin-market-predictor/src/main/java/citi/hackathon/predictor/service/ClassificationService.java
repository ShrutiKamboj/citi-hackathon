package citi.hackathon.predictor.service;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import citi.hackathon.predictor.model.TweetClassificationResponse;
import citi.hackathon.predictor.model.TweetDetails;

public interface ClassificationService {

	public TweetClassificationResponse classifySingleTweet(String tweetText) throws Exception;

	public List<TweetClassificationResponse> classifyMultipleTweets(List<String> tweets) throws Exception;

	public Future<HashMap<String, Integer>> getClassification(String text) throws UnsupportedEncodingException, InterruptedException, ExecutionException;

}