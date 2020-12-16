package com.emt.etl

import java.time.LocalDateTime

case class BiciMadGo (bikeInStation:Int,
                      bikeNum:String,
                      battery:Int,
                      coordX:Double,
                      coordY:Double,
                      address:String,
                      datePosition:LocalDateTime,
                      lastUpdate:LocalDateTime)
