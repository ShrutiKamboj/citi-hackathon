package citi.hackathon.predictor.service;

import java.util.List;

import citi.hackathon.predictor.model.TweetClassificationResponse;

public interface ClassificationService {

	public TweetClassificationResponse classifySingleTweet(String tweetText) throws Exception;

	public List<TweetClassificationResponse> classifyMultipleTweets(List<String> tweets) throws Exception;

}