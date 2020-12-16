package com.emt.etl

import com.emt.etl.BiciMadGo
import org.json4s.{JField, JString, _}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter

object ParseEMTObject{
  def parseEMT(json: String) = {
    println("Param: "+ json)
    implicit val formats = DefaultFormats

    val jValue = parse(json)
    /*removeField {
      case JField("Speed", _) => true
      case JField("qrcode", _) => true
      case JField("Batery", _) => true
      case JField("rangeEstimated", _) => true
      case _ => false
    }*/
    println("Bici: " + jValue)
    val bikeInStation = (jValue\"bike_in_station").extract[Int]
    val deviceName = (jValue\"DeviceName").extract[String]
    val porcBattery = (jValue\"porcBattery").extract[Int]
    val coord = (jValue\"geometry"\"coordinates").extract[Array[Double]]
    val coordX = coord(0)
    val coordY = coord(1)
    val address = (jValue\"Address").extract[String]
    val datePosition = (jValue\"datePosition").extract[String]
    val lastUpdate = (jValue\"lastUpdate").extract[String]

    val formatterPosition = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    val formatterUpdate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

    val bike = new BiciMadGo(bikeInStation,deviceName,porcBattery.toInt,coordX,coordY,address,
      LocalDateTime.parse(datePosition,formatterPosition),LocalDateTime.parse(lastUpdate,formatterUpdate))
    println("New bike" + bike)
      bike
  }
}
