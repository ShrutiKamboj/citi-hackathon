package citi.hackathon.predictor.service.impl;

import java.util.ArrayList;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import citi.hackathon.predictor.model.MappedLocation;
import citi.hackathon.predictor.model.MappedTrends;
import citi.hackathon.predictor.model.PlaceType;
import citi.hackathon.predictor.model.TrendingTopics;
import citi.hackathon.predictor.service.MarketPredictorService;
import twitter4j.Location;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.ResponseList;
import twitter4j.Trend;
import twitter4j.Trends;
import twitter4j.Twitter;
import twitter4j.TwitterException;

@Service
public class MarketPredictorServiceImpl implements MarketPredictorService {

	@Autowired
	Twitter twitter;
	
	@Override
	public List<MappedLocation> getLocations() {
		// TODO Auto-generated method stub
	    ResponseList<Location> result = null;
	    List<MappedLocation> response = new ArrayList<MappedLocation>();
	
		try {
			result = twitter.getAvailableTrends();
			
		} catch (TwitterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
		for (Location value: result) {
			MappedLocation mappedLoc = new MappedLocation();
			mappedLoc.setName(value.getName());
			PlaceType pType = new PlaceType();
			pType.setCode(value.getPlaceCode());
			pType.setName(value.getPlaceName());
			mappedLoc.setPlaceType(pType);
			mappedLoc.setWoeid((long) value.getWoeid());
			mappedLoc.setCountry(value.getCountryName());
			mappedLoc.setCountryCode(value.getCountryCode());
			response.add(mappedLoc);
		}
		return response;
	}

	@Override
	public MappedTrends getTrendingTopics(int placeId) {
		// TODO Auto-generated method stub
		Trend[] trends = null;
		MappedTrends mappedTrends = new MappedTrends();
		List<TrendingTopics> listOfTTopics = new ArrayList<TrendingTopics>();
		
		try {
			trends = twitter.getPlaceTrends(placeId).getTrends();
		} catch (TwitterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for (int i=0;i<trends.length;i++) {
			TrendingTopics tTopic = new TrendingTopics();
			tTopic.setName(trends[i].getName());
			tTopic.setQuery(trends[i].getQuery());
			tTopic.setUrl(trends[i].getURL());
			listOfTTopics.add(tTopic);
		}
		
		mappedTrends.setTrends(listOfTTopics);
		return mappedTrends;
	}

}
