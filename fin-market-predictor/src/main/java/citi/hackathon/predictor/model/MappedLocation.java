package citi.hackathon.predictor.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MappedLocation {

	private String name;
    private PlaceType placeType;
    private String country;
    private Long woeid;
    private String countryCode;
}
