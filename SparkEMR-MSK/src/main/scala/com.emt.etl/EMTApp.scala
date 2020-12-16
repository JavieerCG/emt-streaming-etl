package com.emt.etl

import com.emt.etl.ParseEMTJson.parseEMT
import com.emt.etl.Config
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

import java.util.Properties


object EMTApp {

  def main(args: Array[String]): Unit = {
    val param1 = args(0)
    println("Kafka Brokers " + param1)

    val projectConfig = new Config()
    val conf = new SparkConf().setAppName("EMT ETL")
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

    val bikes= stream.map[String](record => parseEMT(record.value()))

    bikes.print()

    val kafkaProducerProps: Properties = {
      val props = new Properties()
      props.put("bootstrap.servers", param1)
      props.put("key.serializer", classOf[StringSerializer].getName)
      props.put("value.serializer", classOf[StringSerializer].getName)
      props
    }

    try{
      bikes.foreachRDD(rdd=> {
        rdd.foreachPartition(partitionBike => {
          val producer = new KafkaProducer[String, String](kafkaProducerProps)
          partitionBike.foreach(bike => {
            producer.send(
              new ProducerRecord[String, String](
                projectConfig.TOPIC_BICI_MAD_GO_STAGE, bike
              )
            )
            println("Bici " + bike + " sent!!")
          })
        })
      })
    }catch{
      case e:Exception => throw new Exception(e.getMessage)
    }

    ssc.start()
    ssc.awaitTermination()
  }
}
