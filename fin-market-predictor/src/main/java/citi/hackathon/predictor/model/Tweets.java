package citi.hackathon.predictor.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Tweets {
	
	private List<TweetDetails> tweetDetails;
	private int sentimentValue;
	private String category;

}
