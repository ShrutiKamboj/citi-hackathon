package citi.hackathon.predictor.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.maps.errors.ApiException;

import citi.hackathon.predictor.model.MappedLocation;
import citi.hackathon.predictor.model.MappedTrends;
import citi.hackathon.predictor.model.Tweets;
import citi.hackathon.predictor.service.MarketPredictorService;

@RestController
public class MarketPredictorController {

	@Autowired
	MarketPredictorService service;
	
	@RequestMapping(value = "/locations", method = RequestMethod.GET)
	public ResponseEntity<List<MappedLocation>> getLocations() throws ApiException, InterruptedException, IOException {
		return new ResponseEntity<List<MappedLocation>>(service.getLocations(), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/trends/locations", method = RequestMethod.GET) 
	public ResponseEntity<MappedTrends> getTrendingTopics(
			@RequestParam int placeId) {
		
		return new ResponseEntity<MappedTrends>(service.getTrendingTopics(placeId), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/tweets/hashtag", method = RequestMethod.GET)
	public ResponseEntity<Tweets> getTweets(
			@RequestParam String hashtag,
			@RequestParam double lat,
			@RequestParam double lng,
			@RequestParam int placeId) {
		return new ResponseEntity<Tweets>(service.getTweets(hashtag, lat, lng, placeId), HttpStatus.OK);
	}
	
}
