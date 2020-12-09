package citi.hackathon.predictor.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TweetClassificationResponse {
	private String tweetText;
	private String calculatedCategory;
}
