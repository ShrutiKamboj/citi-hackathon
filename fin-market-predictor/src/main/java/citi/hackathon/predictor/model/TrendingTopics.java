package citi.hackathon.predictor.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TrendingTopics {

	private String name;
    private String url;
    private String promoted_content;
    private String query;
    private Long tweet_volume;
}
