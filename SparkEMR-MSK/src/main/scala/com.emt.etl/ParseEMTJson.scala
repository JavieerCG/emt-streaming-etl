package com.emt.etl

import org.json4s.{DefaultFormats, JValue}
import org.json4s.jackson.JsonMethods.{compact, parse, render}
import org.json4s.JsonDSL._
import java.time.format.DateTimeFormatter

object ParseEMTJson {

  def parseEMT(json: String) = {
      println("Param: "+ json)
      implicit val formats = DefaultFormats

      val jValue = parse(json)

      println("Bici: " + jValue)
      val bikeInStation = (jValue\"bike_in_station").extract[Int]
      val deviceName = (jValue\"DeviceName").extract[String]
      val porcBattery = (jValue\"porcBattery").extract[Int]
      val coord = (jValue\"geometry"\"coordinates").extract[Array[Double]]
      val coordX = coord(0)
      val coordY = coord(1)
      val address = (jValue\"Address").extract[String].replaceAll(",", " ")
      val datePosition = (jValue\"datePosition").extract[String]
      val lastUpdate = (jValue\"lastUpdate").extract[String]

      val newJson:JValue = ("bikeNum" -> deviceName) ~
        ("battery" -> porcBattery)~
        ("location" -> ("lat"->coordY) ~ ("lon"->coordX) ) ~
        ("coordX" -> coordX) ~
        ("coordY" -> coordY) ~
        ("address" -> address) ~
        ("datePosition" -> datePosition) ~
        ("lastUpdate" -> lastUpdate)

      compact(render(newJson))
    }
}
