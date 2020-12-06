package citi.hackathon.predictor.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

@Configuration
public class TwitterConfig {
	
	@Value("${twitter.consumer.key}")
	private String consumer_key;
	
	@Value("${twitter.consumer.secret}")
	private String consumer_secret;
	
	@Value("${twitter.access.token}")
	private String access_token;
	
	@Value("${twitter.access.token.secret}")
	private String access_token_secret;
	
	@Bean
	public Twitter getInstance() {
		ConfigurationBuilder conf = new ConfigurationBuilder();
	
		conf.setDebugEnabled(true)
		  .setOAuthConsumerKey(consumer_key)
		  .setOAuthConsumerSecret(consumer_secret)
		  .setOAuthAccessToken(access_token)
		  .setOAuthAccessTokenSecret(access_token_secret);
		TwitterFactory tf = new TwitterFactory(conf.build());
		Twitter twitter = tf.getInstance();
		
		return twitter;
	}

}
