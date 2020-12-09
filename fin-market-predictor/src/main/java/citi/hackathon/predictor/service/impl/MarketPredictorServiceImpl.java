package citi.hackathon.predictor.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
import citi.hackathon.predictor.model.TweetClassificationResponse;
import citi.hackathon.predictor.model.TweetDetails;
import citi.hackathon.predictor.model.Tweets;
import citi.hackathon.predictor.service.ClassificationService;
import citi.hackathon.predictor.service.MarketPredictorService;
import twitter4j.GeoLocation;
import twitter4j.Location;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Trend;
import twitter4j.Trends;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;

@Service
public class MarketPredictorServiceImpl implements MarketPredictorService {

	@Autowired
	Twitter twitter;
	
	@Autowired
	ClassificationService classificationService;
	
	Map<String,Integer> categoryMap = new HashMap<String, Integer>();
	
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
	public Tweets getTweets(String hashtag, double lat, double lng, int placeId) throws Exception {
		// TODO Auto-generated method stub
		Tweets tweets = new Tweets();
		double cumulativeSentiment = 0;
		int noOfTweets = 0;
		List<TweetDetails> tDetails = new ArrayList<TweetDetails>();
		QueryResult result = null;
		
		Query query = new Query();
		if (placeId != 1) {
			query.setGeoCode(new GeoLocation(lat, lng), 1000.0, Query.MILES);
		}
		query.setCount(200);
		query.setLocale("en");
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
				if (st.getLang().equalsIgnoreCase("en")) {
					String text = st.getText();
					TweetDetails td = new TweetDetails();
					td.setDescription(text);
					int sentiment = getSentiment(text);
					td.setSentiment(sentiment);
					tDetails.add(td);
					cumulativeSentiment += sentiment;
					noOfTweets +=1;
					TweetClassificationResponse response = classificationService.classifySingleTweet(text);
					if (categoryMap.containsKey(response.getCalculatedCategory())) {
						int value = categoryMap.get(response.getCalculatedCategory());
						categoryMap.put(response.getCalculatedCategory(), value +1);
					} else {
						categoryMap.put(response.getCalculatedCategory(), 1);
					}
				}
			}
				
		}
		String category = getCategory(categoryMap);
	//	System.out.println(cumulativeSentiment + " " + noOfTweets);
		cumulativeSentiment = cumulativeSentiment/noOfTweets;
	//	System.out.println(cumulativeSentiment + " " + noOfTweets);
		int roundedSentiment = roundOff(cumulativeSentiment);
		
		tweets.setTweetDetails(tDetails);
		tweets.setSentimentValue(roundedSentiment);
		return tweets;
	}

	private String getCategory(Map<String, Integer> categoryMap) {
		// TODO Auto-generated method stub
		
		String category = null;
		int maxVal=0;
		Set<String> keys = categoryMap.keySet();
		for (String key:keys) {
			System.out.println("Map values " + key + " " + categoryMap.get(key));
			if (categoryMap.get(key)>maxVal) {
				maxVal = categoryMap.get(key);
				category = key;
			}
		}
		return null;
	}

	private int roundOff(double cumulativeSentiment) {
		// TODO Auto-generated method stub
		if (cumulativeSentiment <= 0.5)
			return 0;
		else if (cumulativeSentiment > 0.5 && cumulativeSentiment <= 1.5)
			return 1;
		else if (cumulativeSentiment > 1.5 && cumulativeSentiment <= 2.5)
			return 2;
		else if (cumulativeSentiment > 2.5 && cumulativeSentiment <= 3.5)
			return 3;
		else if (cumulativeSentiment > 3.5 && cumulativeSentiment <= 4.5)
			return 4;
		else if (cumulativeSentiment > 4.5 && cumulativeSentiment <= 5)
			return 5;
		return 2;
	}

	private int getSentiment(String line) {
		// TODO Auto-generated method stub
		int sentiment=0;
		Properties properties = new Properties();
        properties.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
        StanfordCoreNLP stanfordCoreNLP = new StanfordCoreNLP(properties);
        int mainSentiment = 0;
        if (line != null && !line.isEmpty()) {
            int longest = 0;
            Annotation annotation = stanfordCoreNLP.process(line);
            for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
                Tree tree = sentence.get(SentimentCoreAnnotations.AnnotatedTree.class);
                sentiment = RNNCoreAnnotations.getPredictedClass(tree);
                String partText = sentence.toString();
                if (partText.length() > longest) {
                    mainSentiment = sentiment;
                    longest = partText.length();
                }
            }
        }
		return sentiment;
	}

}
