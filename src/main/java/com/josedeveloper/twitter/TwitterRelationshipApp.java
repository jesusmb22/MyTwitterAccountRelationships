package com.josedeveloper.twitter;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class TwitterRelationshipApp {
	
	static final String TWITTER_DB_PATH = "DATABASE_PATH";
	static final String CONSUMER_KEY = "YOUR_CONSUMER_KEY";
	static final String CONSUMER_SECRET = "YOUR_CONSUMER_SECRET";
	static final String ACCESS_TOKEN = "YOUR_ACCESS_TOKEN";
	static final String ACCESS_TOKEN_SECRET = "YOUR_ACCESS_TOKEN_SECRET";
	
	private final GraphDatabaseService graphDB;
	private final Set<String> totalUsers;
	private final String account;
	private final int count;
	
	enum NodeType implements Label {
		TWITTER_USER, HASHTAG;
	}
	
	enum Relationships implements RelationshipType {
		USE, MENTION;
	}
	
	public TwitterRelationshipApp(final String account, final int count) {
		this.account = account;
		this.count = count;
		
		totalUsers = new HashSet<>();
		graphDB = new GraphDatabaseFactory().newEmbeddedDatabase(new File(TWITTER_DB_PATH));
	}
	
    public static void main(String[] args) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, ClientProtocolException, IOException
    {	
    	TwitterRelationshipApp app = new TwitterRelationshipApp("josedeveloper", 100);
    	app.registerShutdownHook();
    	app.insertUsers();
    	app.insertUserMentionsRelationshipsByUser();
    }

	private void insertUsers() throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, ClientProtocolException, IOException {
    	OAuthConsumer oAuthConsumer = new CommonsHttpOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
		oAuthConsumer.setTokenWithSecret(ACCESS_TOKEN, ACCESS_TOKEN_SECRET);

		HttpGet httpGet = new HttpGet("https://api.twitter.com/1.1/friends/list.json?screen_name=" + account + "&count=" + count); //those who I follow
		
		oAuthConsumer.sign(httpGet);

		HttpClient httpClient = HttpClientBuilder.create().build();
		HttpResponse httpResponse = httpClient.execute(httpGet);

		//int statusCode = httpResponse.getStatusLine().getStatusCode();
		
		JsonReader reader = Json.createReader(httpResponse.getEntity().getContent());
		JsonObject root = reader.readObject();
		JsonArray users = root.getJsonArray("users");
		
		Iterator<JsonValue> iter = users.iterator();
		while (iter.hasNext()) {
			JsonObject user = (JsonObject) iter.next();
			
			try (Transaction tx = graphDB.beginTx()) {
				Node userNode = graphDB.createNode(NodeType.TWITTER_USER);
				
				userNode.setProperty("id", user.getString("id_str"));
				userNode.setProperty("name", user.getString("name"));
				userNode.setProperty("screen_name", user.getString("screen_name"));
				
				insertRelationshipsWithHashtagsByUser(userNode, graphDB);
				
				tx.success();
			} catch (Exception e) {
				System.out.println(e);
			}
			
			totalUsers.add(user.getString("screen_name"));
		}
	}
		
	private void registerShutdownHook() {
		
		
		    // Registers a shutdown hook for the Neo4j instance so that it
		    // shuts down nicely when the VM exits (even if you "Ctrl-C" the
		    // running application).
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
		    public void run() {
				graphDB.shutdown();
		    }
			
		});
	}
	
	
	private static void insertRelationshipsWithHashtagsByUser(Node user, final GraphDatabaseService db) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, ClientProtocolException, IOException {
		OAuthConsumer oAuthConsumer = new CommonsHttpOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
		oAuthConsumer.setTokenWithSecret(ACCESS_TOKEN, ACCESS_TOKEN_SECRET);
		
		HttpGet httpGet = new HttpGet("https://api.twitter.com/1.1/statuses/user_timeline.json?screen_name=" + user.getProperty("screen_name"));
		oAuthConsumer.sign(httpGet);
		
		HttpClient httpClient = HttpClientBuilder.create().build();
		HttpResponse httpResponse = httpClient.execute(httpGet);
		
		//int statusCode = httpResponse.getStatusLine().getStatusCode();
		
		JsonReader timelineReader = Json.createReader(httpResponse.getEntity().getContent());
		JsonArray tweets = timelineReader.readArray();
		Iterator<JsonValue> tweetsIter = tweets.iterator();
		Map<String, Integer> usedHashtags = new HashMap<>();
		while(tweetsIter.hasNext()) {
			JsonObject tweet = (JsonObject) tweetsIter.next();
			
			JsonObject entities = tweet.getJsonObject("entities");
			JsonArray hashtags = entities.getJsonArray("hashtags");
			Iterator<JsonValue> hashtagsIter = hashtags.iterator();
			
			while (hashtagsIter.hasNext()) {
				String hashtag = ((JsonObject) hashtagsIter.next()).getString("text");
				
				if (usedHashtags.containsKey(hashtag)) {
					Integer counter = usedHashtags.get(hashtag);
					usedHashtags.put(hashtag, ++counter);
				} else{
					usedHashtags.put(hashtag, Integer.valueOf(1));
				}
			}
			
		}
		
		for (String hashtag : usedHashtags.keySet()) {
			
			try (Transaction tx = db.beginTx()) {
				Node hashtagNode = db.findNode(NodeType.HASHTAG, "text", hashtag);
				if (hashtagNode == null)
					hashtagNode = db.createNode(NodeType.HASHTAG);				
				
				hashtagNode.setProperty("text", hashtag);
				
				Integer timesUsed = usedHashtags.get(hashtag);
				Relationship use = user.createRelationshipTo(hashtagNode, Relationships.USE);
				use.setProperty("times", timesUsed);

				tx.success();
			} catch (Exception e) {
				System.out.println(e);
			}
		}
		
		
	}
	
	private void insertUserMentionsRelationshipsByUser() throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, ClientProtocolException, IOException {
		OAuthConsumer oAuthConsumer = new CommonsHttpOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
		oAuthConsumer.setTokenWithSecret(ACCESS_TOKEN, ACCESS_TOKEN_SECRET);
		
		for (String twitterUser : totalUsers) {
			
			HttpGet httpGet = new HttpGet("https://api.twitter.com/1.1/statuses/user_timeline.json?screen_name=" + twitterUser);
			oAuthConsumer.sign(httpGet);
			
			HttpClient httpClient = HttpClientBuilder.create().build();
			HttpResponse httpResponse = httpClient.execute(httpGet);
			
			//int statusCode = httpResponse.getStatusLine().getStatusCode();
			
			JsonReader timelineReader = Json.createReader(httpResponse.getEntity().getContent());
			JsonArray tweets = timelineReader.readArray();
			Iterator<JsonValue> tweetsIter = tweets.iterator();
			Map<String, Integer> userMentionsDone = new HashMap<>();
			while(tweetsIter.hasNext()) {
				JsonObject tweet = (JsonObject) tweetsIter.next();
				
				JsonObject entities = tweet.getJsonObject("entities");
				JsonArray userMentions = entities.getJsonArray("user_mentions");
				Iterator<JsonValue> hashtagsIter = userMentions.iterator();
				
				while (hashtagsIter.hasNext()) {
					String userMentioned = ((JsonObject) hashtagsIter.next()).getString("screen_name");
					
					if (totalUsers.contains(userMentioned)) {
						if (userMentionsDone.containsKey(userMentioned)) {
							Integer counter = userMentionsDone.get(userMentioned);
							userMentionsDone.put(userMentioned, ++counter);
						} else{
							userMentionsDone.put(userMentioned, Integer.valueOf(1));
						}
					}
				}
				
			}
			
			for (String userMentionDone : userMentionsDone.keySet()) {
				
				try (Transaction tx = graphDB.beginTx()) {
					Node twitterUserMentionedNode = graphDB.findNode(NodeType.TWITTER_USER, "screen_name", userMentionDone);
					Node twitterUserNode = graphDB.findNode(NodeType.TWITTER_USER, "screen_name", twitterUser);
					
					Integer timesMentioned = userMentionsDone.get(userMentionDone);
					Relationship use = twitterUserNode.createRelationshipTo(twitterUserMentionedNode, Relationships.MENTION);
					use.setProperty("times", timesMentioned);

					tx.success();
				} catch (Exception e) {
					System.out.println(e);
				}
			}
		}
		
	}
	
}
