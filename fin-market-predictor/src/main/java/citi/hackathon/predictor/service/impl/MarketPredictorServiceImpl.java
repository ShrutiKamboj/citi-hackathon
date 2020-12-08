package citi.hackathon.predictor.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;

import citi.hackathon.predictor.model.MappedLocation;
import citi.hackathon.predictor.model.MappedTrends;
import citi.hackathon.predictor.model.PlaceType;
import citi.hackathon.predictor.model.TrendingTopics;
import citi.hackathon.predictor.model.TweetDetails;
import citi.hackathon.predictor.model.Tweets;
import citi.hackathon.predictor.service.MarketPredictorService;
import twitter4j.Location;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Trend;
import twitter4j.Trends;
import twitter4j.Twitter;
import twitter4j.TwitterException;

@Service
public class MarketPredictorServiceImpl implements MarketPredictorService {

	@Autowired
	Twitter twitter;
	
	@Value("${google.api.key}")
	String google_api_key;
	
	@Override
	public List<MappedLocation> getLocations() throws ApiException, InterruptedException, IOException {
		// TODO Auto-generated method stub
	    ResponseList<Location> result = null;
	    GeoApiContext context = new GeoApiContext.Builder()
			    .apiKey(google_api_key)
			    .build();
	    List<MappedLocation> response = new ArrayList<MappedLocation>();
	
		try {
			result = twitter.getAvailableTrends();
			
		} catch (TwitterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
		for (Location value: result) {
			if (value.getPlaceName().equalsIgnoreCase("country") || value.getPlaceName().equalsIgnoreCase("Supername")) {
				GeocodingResult[] results =  GeocodingApi.geocode(context,
					    value.getName()).await();
				
				LatLng latLang = results[0].geometry.location;
				MappedLocation mappedLoc = new MappedLocation();
				mappedLoc.setName(value.getName());
				PlaceType pType = new PlaceType();
				pType.setCode(value.getPlaceCode());
				pType.setName(value.getPlaceName());
				mappedLoc.setPlaceType(pType);
				mappedLoc.setWoeid((long) value.getWoeid());
				mappedLoc.setCountry(value.getCountryName());
				mappedLoc.setCountryCode(value.getCountryCode());
				mappedLoc.setLat(latLang.lat);
				mappedLoc.setLang(latLang.lng);
				
				response.add(mappedLoc);
				}
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
			tTopic.setTweet_volume(trends[i].getTweetVolume());
			listOfTTopics.add(tTopic);
		}
		
		List<TrendingTopics> sortedList = listOfTTopics.stream().sorted(new Comparator<TrendingTopics>() {

			@Override
			public int compare(TrendingTopics o1, TrendingTopics o2) {
				// TODO Auto-generated method stub
				
				return o2.getTweet_volume()-o1.getTweet_volume();
			}
		}).collect(Collectors.toList());
		mappedTrends.setTrends(sortedList);
		return mappedTrends;
	}

	@Override
	public Tweets getTweets(String hashtag) {
		// TODO Auto-generated method stub
		Tweets tweets = new Tweets();
		List<TweetDetails> tDetails = new ArrayList<TweetDetails>();
		QueryResult result = null;
		
		Query query = new Query();
		query.setCount(200);
		query.setQuery(hashtag);
		
		try {
			 result = twitter.search(query);
		} catch (TwitterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		result.getTweets().stream().forEach(a->System.out.println(a.getText()));
		if (result != null) {
			for (Status st : result.getTweets() ) {
				TweetDetails td = new TweetDetails();
				td.setDescription(st.getText());
				tDetails.add(td);
			}
				
		}
		
		tweets.setTweetDetails(tDetails);
		return tweets;
	}

}
