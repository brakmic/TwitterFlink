package com.brakmic.flink.demos

import java.util.Date
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala._
import twitter4j._
import twitter4j.conf.ConfigurationBuilder

/**
* Describes a Twitter location as Longitude/Latitude
*/
case class TwitterLocation(var lat: Double, var lon: Double) {
  def this(){
    this(0.0,0.0)
  }
}

/**
* Describes a single tweet
*/
case class Tweet(userName: String, content: String, createdAt: Date, location: TwitterLocation, language: String)
{
  def this(){
    this("__NO_USER__","__NO_CONTENT__",new Date(), new TwitterLocation(0.0,0.0),"")
  }
}

/**
* This class describes the source of incoming twitter feeds and is used by the DataStream API
* It implements the SourceFunction class
*/
class TwitterStreamGenerator(filterTerms: String*) extends SourceFunction[Tweet]{
  var running = true

  override def cancel(): Unit = {
    running = false
  }

  /**
  * This method is being called by DataStream API to kick-off the stream-production
*/
  override def run(ctx: SourceContext[Tweet]): Unit = {
    val cb = new ConfigurationBuilder
    cb.setDebugEnabled(true)
      .setOAuthConsumerKey("")
      .setOAuthConsumerSecret("")
      .setOAuthAccessToken("")
      .setOAuthAccessTokenSecret("")

    val stream: TwitterStream = new TwitterStreamFactory(cb.build()).getInstance()

    stream.addListener(new SimpleStreamListener(ctx))
    val query = new FilterQuery(0, null, filterTerms.toArray)
    stream.filter(query)
    while(running){}
  }
}

/**
* This class represents the Listener needed by Twitter4J
* Here we define methods which are being called by the Twitter API (especially the "onStatus" method is very important)
* To make the DataStream API aware of our incoming tweets we instantiate SimpleStreamListener by providing it the
* SourceContext instance delivered by DataStream API. Inside onStatus method the collect() method of SourceContext
* will be called to forward new tweets to it. Therefore the SimpleStreamListener is hard-wired with the TwitterStreamGenerator
*/
class SimpleStreamListener(ctx: SourceContext[Tweet]) extends StatusListener() {
  override def onStatus(status: Status) = {
    val userName = status.getUser.getName
    val content = status.getText
    val createdAt = status.getCreatedAt
    val geoLoc = status.getGeoLocation
    val location = geoLoc match {
      case null => new TwitterLocation(0.0,0.0)
      case _ => new TwitterLocation(geoLoc.getLatitude, geoLoc.getLongitude)
    }
    val language = status.getLang
    val tweet = Tweet(userName, content, createdAt, location, language)
    ctx.collect(tweet)
  }

  override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) = {}

  override def onTrackLimitationNotice(numberOfLimitedStatuses: Int) = {}

  override def onException(ex: Exception) = {
    ex.printStackTrace
  }

  def onStallWarning(warning: StallWarning) = {}
  def onScrubGeo(l: Long, l1: Long) = {}
}

/**
* Our application starts here.
* We get the StreamingContext and declare a new Stream-Source by instatiating the TwitterStreamGenerator
* which will only forward tweets containing certain #hash-tags
*/
object TwitterStreamDemo {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val tweets: DataStream[Tweet] = env.addSource(new TwitterStreamGenerator("#machinelearning","#datascience"))
    tweets.map(tweet => println(s"$tweet.userName : $tweet.content"))
    env.execute("TwitterStream")
  }
}
