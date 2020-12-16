package com.emt.etl

import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.kafka.common.serialization.Serializer

import java.io.ObjectOutputStream
import java.util

class BiciMadGoSerializer extends Serializer[BiciMadGo]{
  override def configure(map: util.Map[String, _], b: Boolean): Unit = {}

  override def serialize(topic: String, data: BiciMadGo): Array[Byte] = {
    try {
      val byteOut = new ByteArrayOutputStream()
      val objOut = new ObjectOutputStream(byteOut)
      objOut.writeObject(data)
      objOut.close()
      byteOut.close()
      byteOut.toByteArray
    }    catch {
      case ex:Exception => throw new Exception(ex.getMessage)
    }
  }

  override def close(): Unit = ???
}
