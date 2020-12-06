package citi.hackathon.predictor.service;

import java.util.List;

import org.springframework.stereotype.Service;

import citi.hackathon.predictor.model.MappedLocation;

public interface MarketPredictorService {
	
	public List<MappedLocation> getLocations();

}
