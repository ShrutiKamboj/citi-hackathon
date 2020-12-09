package citi.hackathon.predictor.service;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.google.maps.errors.ApiException;

import citi.hackathon.predictor.model.MappedLocation;
import citi.hackathon.predictor.model.MappedTrends;
import citi.hackathon.predictor.model.Tweets;

public interface MarketPredictorService {
	
	public List<MappedLocation> getLocations() throws ApiException, InterruptedException, IOException;

	public MappedTrends getTrendingTopics(int placeId);

	public Tweets getTweets(String hashtag, double lat, double lng, int placeId);

}
