package citi.hackathon.predictor.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import citi.hackathon.predictor.model.MappedLocation;
import citi.hackathon.predictor.model.PlaceType;
import citi.hackathon.predictor.service.MarketPredictorService;
import twitter4j.Location;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.ResponseList;
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

}
