package com.emt.etl

import com.emt.etl.ParseEMTObject.parseEMT
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.elasticsearch.spark.streaming.sparkDStreamFunctions

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties


object EMTElasticApp {

  def main(args: Array[String]): Unit = {
    val param1 = args(0)
    println("Kafka Brokers " + param1)
    val param2 = args(1)
    println("Elasticseach server " + param2 )
/*    val param2 = args(1)
    println("Elastic URL "+ param2)*/
    val projectConfig = new Config()
    val conf = new SparkConf().setAppName("EMT ETL")
      .set("spark.es.nodes",param2)
      .set("spark.es.port","9200")
      .set("spark.es.nodes.wan.only","true")
    val ssc = new StreamingContext(conf, Seconds(1))

    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> param1,
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "EMT",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )
    val topics = Array(projectConfig.TOPIC_BICI_MAD_GO_RAW)
    val stream = KafkaUtils.createDirectStream[String, String](
      ssc,
      PreferConsistent,
      Subscribe[String, String](topics, kafkaParams)
    )

    val bikes= stream.map[BiciMadGo](record => parseEMT(record.value()))

    bikes.print()

    val dtf = DateTimeFormatter.ofPattern("yyyy.MM.dd")

    val date= LocalDate.now().format(dtf)
    bikes.saveToEs("emt-data-"+date)

    ssc.start()
    ssc.awaitTermination()
  }
}
