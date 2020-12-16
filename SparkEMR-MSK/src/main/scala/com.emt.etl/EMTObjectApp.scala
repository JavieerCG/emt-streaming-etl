package com.emt.etl

import com.emt.etl.ParseEMTObject.parseEMT
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.{Seconds, StreamingContext}

import java.util.Properties


object EMTObjectApp {

  def main(args: Array[String]): Unit = {
    val param1 = args(0)
    println("Kafka Brokers " + param1)
/*    val param2 = args(1)
    println("Elastic URL "+ param2)*/
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

    val bikes= stream.map[BiciMadGo](record => parseEMT(record.value()))

    bikes.print()

    val kafkaProducerProps: Properties = {
      val props = new Properties()
      props.put("bootstrap.servers", param1)
      props.put("key.serializer", classOf[StringSerializer].getName)
      props.put("value.serializer", "com.emt.etl.BiciMadGoSerializer")
      props
    }

    try{
      bikes.foreachRDD(rdd=> {
        rdd.foreachPartition(partitionBike => {
          val producer = new KafkaProducer[String, BiciMadGo](kafkaProducerProps)
          partitionBike.foreach(bike => {
            producer.send(
              new ProducerRecord[String, BiciMadGo](
                projectConfig.TOPIC_BICI_MAD_GO_STAGE, bike.bikeNum, bike
              )
            )
            println("Bici " + bike.bikeNum + " sent!!")
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
