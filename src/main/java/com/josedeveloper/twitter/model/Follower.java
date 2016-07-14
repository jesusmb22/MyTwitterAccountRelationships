package com.josedeveloper.twitter.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Follower {
	
	private String id;
	private String userName;
	private String userScreenName;
	private Map<String, Integer> hashtags;
	private Set<String> mentions;
	
	public Follower() {
		hashtags = new HashMap<>();
		mentions = new HashSet<>();
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public String getUserScreenName() {
		return userScreenName;
	}
	public void setUserScreenName(String userScreenName) {
		this.userScreenName = userScreenName;
	}
	public Map<String, Integer> getHashtags() {
		return hashtags;
	}
	public void setHastags(Map<String, Integer> hashtags) {
		this.hashtags = hashtags;
	}
	public void setMentions(Set<String> mentions) {
		this.mentions = mentions;
	}
	public Set<String> getMentions() {
		return mentions;
	}
	
}
