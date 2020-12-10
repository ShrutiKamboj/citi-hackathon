package citi.hackathon.predictor.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PredictionsData {
	
	List<String> label;
	List<Integer> pastTrends;
	List<Integer> futurePredictions;
}
