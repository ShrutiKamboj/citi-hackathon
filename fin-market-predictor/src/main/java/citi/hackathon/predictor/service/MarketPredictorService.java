package citi.hackathon.predictor.service;

import java.util.List;

import org.springframework.stereotype.Service;

import citi.hackathon.predictor.model.MappedLocation;
import citi.hackathon.predictor.model.MappedTrends;

public interface MarketPredictorService {
	
	public List<MappedLocation> getLocations();

	public MappedTrends getTrendingTopics(int placeId);

}
