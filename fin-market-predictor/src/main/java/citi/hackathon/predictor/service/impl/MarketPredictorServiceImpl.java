package citi.hackathon.predictor.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
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
import citi.hackathon.predictor.model.PredictionsData;
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
	List<String> locCollections = Arrays.asList("Canada","India","New Zealand", "Australia","United Kingdom","United States", "Russia", "France",
			"Turkey","Italy","Israel","Japan","Korea","Pakistan","Germany","Malaysia","Singapore");

	List<String> label = Arrays.asList("Consumer and retail" + 
			"Tourism", "IT/FinTech", "Health", "Logistics");

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
			if ((locCollections.contains(value.getCountryName()) || value.getCountryName().equals("")) && (value.getPlaceName().equalsIgnoreCase("country") || value.getPlaceName().equalsIgnoreCase("Supername"))) {
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
		List<TrendingTopics> truncatedList = new ArrayList<TrendingTopics>();
		for (int i=0;i<10;i++) {
			truncatedList.add(sortedList.get(i));
		}
		mappedTrends.setTrends(truncatedList);
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
		List<String> multipleTweets = new ArrayList<String>();
		for (TweetDetails td : tDetails) {
			multipleTweets.add(td.getDescription());
		}
		String category = getCategory(categoryMap);
		//	System.out.println(cumulativeSentiment + " " + noOfTweets);
		cumulativeSentiment = cumulativeSentiment/noOfTweets;
		//	System.out.println(cumulativeSentiment + " " + noOfTweets);
		int roundedSentiment = roundOff(cumulativeSentiment);

		tweets.setTweetDetails(tDetails);
		tweets.setSentimentValue(roundedSentiment);
		tweets.setCategory(category);
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
		return category;
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

	@Override
	public PredictionsData getPredictionData(int sentiment, String country, String category) {
		// TODO Auto-generated method stub
		PredictionsData predData = new PredictionsData();


		predData.setLabel(label);
		predData.setFuturePredictions(getPastTrends(country, category, sentiment));
		predData.setPastTrends(getFuturePredictions(country, category, sentiment));

	 
		return predData;
	}

	private List<Integer> getFuturePredictions(String country, String category, int sentiment) {
	// TODO Auto-generated method stub
		if (country.equalsIgnoreCase("India")) {
			if (category.equalsIgnoreCase("government")) {
				 switch (sentiment) {
					 case 0:
						 return Arrays.asList(70,54,63,62,63);
					 case 1:
						 return Arrays.asList(75,58,62,63,64);
					 case 2:
						 return Arrays.asList(80,60,89,77,62);
					 case 3:
						 return Arrays.asList(86,64,65,88,76);
					 case 4:
						 return Arrays.asList(60,50,60,60,65);
					 case 5:
						 return Arrays.asList(60,50,60,60,65);
				 }
			} else if (category.equalsIgnoreCase("Disaster")) {
				 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(80,32,87,54,87);
				 case 2:
					 return Arrays.asList(70,20,75,66,76);
				 case 3:
					 return Arrays.asList(90,17,54,78,65);
				 case 4:
					 return Arrays.asList(60,34,78,76,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 } }else if (category.equalsIgnoreCase("Religion")) {
				 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(80,32,87,54,87);
				 case 2:
					 return Arrays.asList(70,20,75,66,76);
				 case 3:
					 return Arrays.asList(90,17,54,78,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 } }else if (category.equalsIgnoreCase("War")) {
				 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(80,32,87,54,87);
				 case 2:
					 return Arrays.asList(70,20,75,66,76);
				 case 3:
					 return Arrays.asList(90,17,54,78,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }			
		}
	}
	if (country.equalsIgnoreCase("Canada"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(69,65,88,76,65);
				 case 2:
					 return Arrays.asList(82,34,78,75,65);
				 case 3:
					 return Arrays.asList(88,89,76,67,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(69,65,88,76,65);
			 case 2:
				 return Arrays.asList(82,34,78,75,65);
			 case 3:
				 return Arrays.asList(88,89,76,67,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(69,65,88,76,65);
			 case 2:
				 return Arrays.asList(82,34,78,75,65);
			 case 3:
				 return Arrays.asList(88,89,76,67,65);
		     case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(69,65,88,76,65);
			 case 2:
				 return Arrays.asList(82,34,78,75,65);
			 case 3:
				 return Arrays.asList(88,89,76,67,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("New Zealand"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(89,50,54,57,65);
				 case 2:
					 return Arrays.asList(78,54,64,68,65);
				 case 3:
					 return Arrays.asList(76,53,62,69,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(89,50,54,57,65);
			 case 2:
				 return Arrays.asList(78,32,45,78,65);
			 case 3:
				 return Arrays.asList(76,20,34,65,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(89,23,54,88,65);
			 case 2:
				 return Arrays.asList(78,34,43,76,75);
			 case 3:
				 return Arrays.asList(76,24,62,75,76);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(89,20,54,70,76);
			 case 2:
				 return Arrays.asList(78,16,64,69,63);
			 case 3:
				 return Arrays.asList(76,27,62,68,73);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Australia"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(89,20,54,70,76);
				 case 2:
					 return Arrays.asList(78,16,64,69,63);
				 case 3:
					 return Arrays.asList(76,27,62,68,73);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(89,20,54,70,76);
			 case 2:
				 return Arrays.asList(78,16,64,69,63);
			 case 3:
				 return Arrays.asList(76,27,62,68,73);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(89,20,54,70,76);
			 case 2:
				 return Arrays.asList(78,16,64,69,63);
			 case 3:
				 return Arrays.asList(76,27,62,68,73);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(89,20,54,70,76);
			 case 2:
				 return Arrays.asList(78,16,64,78,63);
			 case 3:
				 return Arrays.asList(80,27,62,68,73);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("United States"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,36,60,60,65);
				 case 2:
					 return Arrays.asList(60,35,66,60,65);
				 case 3:
					 return Arrays.asList(60,20,60,68,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,60,65);
			 case 2:
				 return Arrays.asList(60,15,60,66,65);
			 case 3:
				 return Arrays.asList(60,45,54,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,39,60,60,65);
			 case 2:
				 return Arrays.asList(62,34,68,60,65);
			 case 3:
				 return Arrays.asList(60,59,60,69,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,23,66,68,65);
			 case 2:
				 return Arrays.asList(60,25,64,68,65);
			 case 3:
				 return Arrays.asList(60,44,63,89,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("United Kingdom"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,20,60,87,65);
				 case 2:
					 return Arrays.asList(60,15,60,63,65);
				 case 3:
					 return Arrays.asList(60,36,60,76,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,60,89);
			 case 2:
				 return Arrays.asList(60,17,60,69,65);
			 case 3:
				 return Arrays.asList(60,28,60,68,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,15,60,60,65);
			 case 2:
				 return Arrays.asList(60,28,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,87,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,23,60,60,78);
			 case 2:
				 return Arrays.asList(60,25,60,60,56);
			 case 3:
				 return Arrays.asList(60,50,60,60,98);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Pakistan"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,60,65);
				 case 2:
					 return Arrays.asList(50,20,60,58,65);
				 case 3:
					 return Arrays.asList(70,30,60,76,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,65);
			 case 2:
				 return Arrays.asList(60,23,60,87,65);
			 case 3:
				 return Arrays.asList(60,25,60,90,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,15,60,56,65);
			 case 2:
				 return Arrays.asList(60,20,60,87,65);
			 case 3:
				 return Arrays.asList(60,15,60,56,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,76,65);
			 case 2:
				 return Arrays.asList(60,30,60,89,65);
			 case 3:
				 return Arrays.asList(60,30,60,76,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Korea"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,20,60,87,65);
				 case 2:
					 return Arrays.asList(60,15,60,63,65);
				 case 3:
					 return Arrays.asList(60,36,60,76,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,60,89);
			 case 2:
				 return Arrays.asList(60,17,60,69,65);
			 case 3:
				 return Arrays.asList(60,28,60,68,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,15,60,60,65);
			 case 2:
				 return Arrays.asList(60,28,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,87,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,23,60,60,78);
			 case 2:
				 return Arrays.asList(60,25,60,60,56);
			 case 3:
				 return Arrays.asList(60,50,60,60,98);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Russia"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,27,66,76,65);
				 case 2:
					 return Arrays.asList(60,28,68,60,65);
				 case 3:
					 return Arrays.asList(60,29,76,78,88);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,87,34,65);
			 case 2:
				 return Arrays.asList(60,20,60,73,65);
			 case 3:
				 return Arrays.asList(60,30,60,76,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,62);
			 case 2:
				 return Arrays.asList(60,20,60,60,68);
			 case 3:
				 return Arrays.asList(60,30,60,60,69);
			 case 4:
				 return Arrays.asList(60,50,60,60,78);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,67,65);
			 case 2:
				 return Arrays.asList(60,30,60,69,65);
			 case 3:
				 return Arrays.asList(60,40,60,72,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("France"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,30,60,76,65);
				 case 2:
					 return Arrays.asList(60,40,60,78,65);
				 case 3:
					 return Arrays.asList(60,50,60,64,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,43,60,65);
			 case 2:
				 return Arrays.asList(60,30,48,75,65);
			 case 3:
				 return Arrays.asList(60,40,84,60,86);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,43,60,65);
			 case 2:
				 return Arrays.asList(60,30,48,75,65);
			 case 3:
				 return Arrays.asList(60,40,84,60,86);
			  case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,43,60,65);
			 case 2:
				 return Arrays.asList(60,30,48,75,65);
			 case 3:
				 return Arrays.asList(60,40,84,60,86);
			  case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Turkey"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,34,60,84);
				 case 2:
					 return Arrays.asList(60,20,45,60,94);
				 case 3:
					 return Arrays.asList(60,30,60,78,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,78,65);
			 case 2:
				 return Arrays.asList(60,30,60,77,65);
			 case 3:
				 return Arrays.asList(60,40,60,76,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,75,65);
			 case 2:
				 return Arrays.asList(60,30,60,38,65);
			 case 3:
				 return Arrays.asList(60,40,60,86,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,60,65);
			 case 2:
				 return Arrays.asList(60,30,78,81,65);
			 case 3:
				 return Arrays.asList(60,40,85,82,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Italy"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,10,60,60,65);
				 case 1:
					 return Arrays.asList(60,20,60,72,65);
				 case 2:
					 return Arrays.asList(60,30,60,73,65);
				 case 3:
					 return Arrays.asList(60,40,60,76,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,60,65);
			 case 2:
				 return Arrays.asList(70,30,60,83,65);
			 case 3:
				 return Arrays.asList(90,40,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(70,20,60,60,65);
			 case 2:
				 return Arrays.asList(80,30,68,70,85);
			 case 3:
				 return Arrays.asList(60,40,60,80,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(76,10,60,71,65);
			 case 2:
				 return Arrays.asList(78,20,60,73,86);
			 case 3:
				 return Arrays.asList(79,30,60,86,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Israel"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,60,65);
				 case 2:
					 return Arrays.asList(50,20,60,58,65);
				 case 3:
					 return Arrays.asList(70,30,60,76,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,65);
			 case 2:
				 return Arrays.asList(60,23,60,87,65);
			 case 3:
				 return Arrays.asList(60,25,60,90,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,15,60,56,65);
			 case 2:
				 return Arrays.asList(60,20,60,87,65);
			 case 3:
				 return Arrays.asList(60,15,60,56,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,76,65);
			 case 2:
				 return Arrays.asList(60,30,60,89,65);
			 case 3:
				 return Arrays.asList(60,30,60,76,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Japan"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,20,60,60,65);
				 case 1:
					 return Arrays.asList(60,30,60,85,65);
				 case 2:
					 return Arrays.asList(60,40,60,76,78);
				 case 3:
					 return Arrays.asList(60,50,60,78,98);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,30,60,60,72);
			 case 2:
				 return Arrays.asList(60,40,60,89,78);
			 case 3:
				 return Arrays.asList(60,50,86,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,73,65);
			 case 2:
				 return Arrays.asList(60,30,83,85,82);
			 case 3:
				 return Arrays.asList(60,40,60,90,72);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,92,65);
			 case 2:
				 return Arrays.asList(60,20,60,78,65);
			 case 3:
				 return Arrays.asList(60,30,60,72,63);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Germany"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,20,60,60,65);
				 case 2:
					 return Arrays.asList(60,30,60,72,65);
				 case 3:
					 return Arrays.asList(60,40,75,60,85);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,93,65);
			 case 2:
				 return Arrays.asList(60,30,60,75,88);
			 case 3:
				 return Arrays.asList(60,40,60,83,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,62,73);
			 case 2:
				 return Arrays.asList(60,20,68,84,82);
			 case 3:
				 return Arrays.asList(60,30,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,65);
			 case 2:
				 return Arrays.asList(60,20,60,72,82);
			 case 3:
				 return Arrays.asList(60,30,60,85,54);
			 case 4:
				 return Arrays.asList(60,40,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Malaysia"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,72,88);
				 case 2:
					 return Arrays.asList(60,20,72,60,65);
				 case 3:
					 return Arrays.asList(60,30,60,73,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,63,65);
			 case 2:
				 return Arrays.asList(60,20,60,68,72);
			 case 3:
				 return Arrays.asList(60,30,60,62,85);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,65);
			 case 2:
				 return Arrays.asList(60,20,85,60,73);
			 case 3:
				 return Arrays.asList(60,30,60,60,63);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,72);
			 case 2:
				 return Arrays.asList(60,20,60,78,65);
			 case 3:
				 return Arrays.asList(60,30,60,60,83);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Singapore"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,60,65);
				 case 2:
					 return Arrays.asList(60,20,60,74,87);
				 case 3:
					 return Arrays.asList(60,30,60,72,64);
				 case 4:
					 return Arrays.asList(60,50,60,98,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,69);
			 case 2:
				 return Arrays.asList(60,20,75,60,66);
			 case 3:
				 return Arrays.asList(60,30,72,78,65);
			 case 4:
				 return Arrays.asList(60,40,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,98,65);
			 case 2:
				 return Arrays.asList(60,20,60,72,69);
			 case 3:
				 return Arrays.asList(60,30,60,78,73);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,65);
			 case 2:
				 return Arrays.asList(60,20,60,60,76);
			 case 3:
				 return Arrays.asList(60,30,60,78,65);
			 case 4:
				 return Arrays.asList(60,40,60,60,82);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
		 }
	return Arrays.asList(60,50,60,60,65);
	}

	private List<Integer> getPastTrends(String country, String category, int sentiment) {
	// TODO Auto-generated method stub
		if (country.equalsIgnoreCase("India")) {
			if (category.equalsIgnoreCase("government")) {
				 switch (sentiment) {
					 case 0:
						 return Arrays.asList(60,50,60,60,65);
					 case 1:
						 return Arrays.asList(60,10,60,72,88);
					 case 2:
						 return Arrays.asList(60,20,72,60,65);
					 case 3:
						 return Arrays.asList(60,30,60,73,65);
					 case 4:
						 return Arrays.asList(60,50,60,60,65);
					 case 5:
						 return Arrays.asList(60,50,60,60,65);
				 }
			} else if (category.equalsIgnoreCase("Disaster")) {
				 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,63,65);
				 case 2:
					 return Arrays.asList(60,20,60,68,72);
				 case 3:
					 return Arrays.asList(60,30,60,62,85);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 } }else if (category.equalsIgnoreCase("Religion")) {
				 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,60,65);
				 case 2:
					 return Arrays.asList(60,20,85,60,73);
				 case 3:
					 return Arrays.asList(60,30,60,60,63);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 } }else if (category.equalsIgnoreCase("War")) {
				 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,60,72);
				 case 2:
					 return Arrays.asList(60,20,60,78,65);
				 case 3:
					 return Arrays.asList(60,30,60,60,83);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }		
		}
	}
	if (country.equalsIgnoreCase("Canada"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,60,65);
				 case 2:
					 return Arrays.asList(60,20,60,74,87);
				 case 3:
					 return Arrays.asList(60,30,60,72,64);
				 case 4:
					 return Arrays.asList(60,50,60,98,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,69);
			 case 2:
				 return Arrays.asList(60,20,75,60,66);
			 case 3:
				 return Arrays.asList(60,30,72,78,65);
			 case 4:
				 return Arrays.asList(60,40,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,98,65);
			 case 2:
				 return Arrays.asList(60,20,60,72,69);
			 case 3:
				 return Arrays.asList(60,30,60,78,73);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,65);
			 case 2:
				 return Arrays.asList(60,20,60,60,76);
			 case 3:
				 return Arrays.asList(60,30,60,78,65);
			 case 4:
				 return Arrays.asList(60,40,60,60,82);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	
	if (country.equalsIgnoreCase("Germany"))
			if (category.equalsIgnoreCase("government")) {
				 switch (sentiment) {
					 case 0:
						 return Arrays.asList(60,50,60,60,65);
					 case 1:
						 return Arrays.asList(60,20,60,60,65);
					 case 2:
						 return Arrays.asList(60,30,60,72,65);
					 case 3:
						 return Arrays.asList(60,40,75,60,85);
					 case 4:
						 return Arrays.asList(60,50,60,60,65);
					 case 5:
						 return Arrays.asList(60,50,60,60,65);
				 }
			} else if (category.equalsIgnoreCase("Disaster")) {
				 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,20,60,93,65);
				 case 2:
					 return Arrays.asList(60,30,60,75,88);
				 case 3:
					 return Arrays.asList(60,40,60,83,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 } }else if (category.equalsIgnoreCase("Religion")) {
				 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,62,73);
				 case 2:
					 return Arrays.asList(60,20,68,84,82);
				 case 3:
					 return Arrays.asList(60,30,60,60,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 } }else if (category.equalsIgnoreCase("War")) {
				 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,60,65);
				 case 2:
					 return Arrays.asList(60,20,60,72,82);
				 case 3:
					 return Arrays.asList(60,30,60,85,54);
				 case 4:
					 return Arrays.asList(60,40,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }						
	}
	if (country.equalsIgnoreCase("Australia"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,50,60,60,65);
				 case 2:
					 return Arrays.asList(60,50,60,60,65);
				 case 3:
					 return Arrays.asList(60,50,60,60,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("United States"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,50,60,60,65);
				 case 2:
					 return Arrays.asList(60,50,60,60,65);
				 case 3:
					 return Arrays.asList(60,50,60,60,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("United Kingdom"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,20,60,60,65);
				 case 2:
					 return Arrays.asList(60,30,60,72,65);
				 case 3:
					 return Arrays.asList(60,40,75,60,85);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,93,65);
			 case 2:
				 return Arrays.asList(60,30,60,75,88);
			 case 3:
				 return Arrays.asList(60,40,60,83,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,62,73);
			 case 2:
				 return Arrays.asList(60,20,68,84,82);
			 case 3:
				 return Arrays.asList(60,30,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,65);
			 case 2:
				 return Arrays.asList(60,20,60,72,82);
			 case 3:
				 return Arrays.asList(60,30,60,85,54);
			 case 4:
				 return Arrays.asList(60,40,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }						
	}
	if (country.equalsIgnoreCase("Pakistan"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,50,60,60,65);
				 case 2:
					 return Arrays.asList(60,50,60,60,65);
				 case 3:
					 return Arrays.asList(60,50,60,60,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Korea"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,20,60,60,65);
				 case 1:
					 return Arrays.asList(60,30,60,85,65);
				 case 2:
					 return Arrays.asList(60,40,60,76,78);
				 case 3:
					 return Arrays.asList(60,50,60,78,98);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,30,60,60,72);
			 case 2:
				 return Arrays.asList(60,40,60,89,78);
			 case 3:
				 return Arrays.asList(60,50,86,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,73,65);
			 case 2:
				 return Arrays.asList(60,30,83,85,82);
			 case 3:
				 return Arrays.asList(60,40,60,90,72);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,92,65);
			 case 2:
				 return Arrays.asList(60,20,60,78,65);
			 case 3:
				 return Arrays.asList(60,30,60,72,63);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Russia"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,50,60,60,65);
				 case 2:
					 return Arrays.asList(60,50,60,60,65);
				 case 3:
					 return Arrays.asList(60,50,60,60,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("France"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,50,60,60,65);
				 case 2:
					 return Arrays.asList(60,50,60,60,65);
				 case 3:
					 return Arrays.asList(60,50,60,60,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Turkey"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,50,60,60,65);
				 case 2:
					 return Arrays.asList(60,50,60,60,65);
				 case 3:
					 return Arrays.asList(60,50,60,60,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Italy"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,20,60,87,65);
				 case 2:
					 return Arrays.asList(60,15,60,63,65);
				 case 3:
					 return Arrays.asList(60,36,60,76,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,60,89);
			 case 2:
				 return Arrays.asList(60,17,60,69,65);
			 case 3:
				 return Arrays.asList(60,28,60,68,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,15,60,60,65);
			 case 2:
				 return Arrays.asList(60,28,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,87,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,23,60,60,78);
			 case 2:
				 return Arrays.asList(60,25,60,60,56);
			 case 3:
				 return Arrays.asList(60,50,60,60,98);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }						
	}
	if (country.equalsIgnoreCase("Israel"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,10,60,60,65);
				 case 2:
					 return Arrays.asList(50,20,60,58,65);
				 case 3:
					 return Arrays.asList(70,30,60,76,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,10,60,60,65);
			 case 2:
				 return Arrays.asList(60,23,60,87,65);
			 case 3:
				 return Arrays.asList(60,25,60,90,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,15,60,56,65);
			 case 2:
				 return Arrays.asList(60,20,60,87,65);
			 case 3:
				 return Arrays.asList(60,15,60,56,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,10,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,76,65);
			 case 2:
				 return Arrays.asList(60,30,60,89,65);
			 case 3:
				 return Arrays.asList(60,30,60,76,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Japan"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,20,60,87,65);
				 case 2:
					 return Arrays.asList(60,15,60,63,65);
				 case 3:
					 return Arrays.asList(60,36,60,76,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,60,89);
			 case 2:
				 return Arrays.asList(60,17,60,69,65);
			 case 3:
				 return Arrays.asList(60,28,60,68,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,15,60,60,65);
			 case 2:
				 return Arrays.asList(60,28,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,87,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,23,60,60,78);
			 case 2:
				 return Arrays.asList(60,25,60,60,56);
			 case 3:
				 return Arrays.asList(60,50,60,60,98);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Germany"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,50,60,60,65);
				 case 2:
					 return Arrays.asList(60,50,60,60,65);
				 case 3:
					 return Arrays.asList(60,50,60,60,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Malaysia"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,50,60,60,65);
				 case 2:
					 return Arrays.asList(60,50,60,60,65);
				 case 3:
					 return Arrays.asList(60,50,60,60,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,50,60,60,65);
			 case 2:
				 return Arrays.asList(60,50,60,60,65);
			 case 3:
				 return Arrays.asList(60,50,60,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }			
	}
	if (country.equalsIgnoreCase("Singapore"))
		if (category.equalsIgnoreCase("government")) {
			 switch (sentiment) {
				 case 0:
					 return Arrays.asList(60,50,60,60,65);
				 case 1:
					 return Arrays.asList(60,36,60,60,65);
				 case 2:
					 return Arrays.asList(60,35,66,60,65);
				 case 3:
					 return Arrays.asList(60,20,60,68,65);
				 case 4:
					 return Arrays.asList(60,50,60,60,65);
				 case 5:
					 return Arrays.asList(60,50,60,60,65);
			 }
		} else if (category.equalsIgnoreCase("Disaster")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,20,60,60,65);
			 case 2:
				 return Arrays.asList(60,15,60,66,65);
			 case 3:
				 return Arrays.asList(60,45,54,60,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("Religion")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,39,60,60,65);
			 case 2:
				 return Arrays.asList(62,34,68,60,65);
			 case 3:
				 return Arrays.asList(60,59,60,69,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 } }else if (category.equalsIgnoreCase("War")) {
			 switch (sentiment) {
			 case 0:
				 return Arrays.asList(60,50,60,60,65);
			 case 1:
				 return Arrays.asList(60,23,66,68,65);
			 case 2:
				 return Arrays.asList(60,25,64,68,65);
			 case 3:
				 return Arrays.asList(60,44,63,89,65);
			 case 4:
				 return Arrays.asList(60,50,60,60,65);
			 case 5:
				 return Arrays.asList(60,50,60,60,65);
		 }						
		 }
	return Arrays.asList(60,50,60,60,65);

	}



}
