# Herramienta de Análisis Geospacial de BiciMAD Go

En este README describiremos varias instalaciones y configuraciones de los componentes del proyecto desplegado en la nube pública de Amazon

### Arquitectura

![Arquitectura](https://github.com/JavieerCG/emt-streaming-etl/blob/main/images/arquitectura.png)

## Apache NiFi

NiFi está desplegado en una maquina EC2 *t3.small*.
Una vez creada hay que seguir los siguientes pasos para desplegar.

```bash
sudo yum install java-1.8.0-openjdk

JAVA_HOME=/usr/lib/jvm/jre-1.8.0-openjdk

wget https://apache.brunneis.com/nifi/1.12.1/nifi-1.12.1-bin.tar.gz 

tar -xzvf nifi-1.12.1-bin.tar.gz

cd nifi-1.12.1/

bin/nifi.sh run
bin/nifi.sh start # iniciar modo daemom
bin/nifi.sh stop. # apagar modo daemon
```

Para acceder a la interfaz hay abrir en el navegador una pagina con la url de la instancia en el puerto 8080 por defect y directorio /nifi --> ec2-xxx-xxx-xxx-xxx.region.compute.amazonaws.com:8080/nifi

## MSK

Para conectarnos al cluster creado de MSK en Amazon utilizaremos un cliente de Kafka, alojado en una maquina EC2 *t3.micro*.
Para desplegar el cliente, hemos utilizado la misma version de Kafka que la del cluster, 2.2.1.

### Cliente de Kafka
```bash
sudo yum install java-1.8.0-openjdk

cp /usr/lib/jvm/java-1.8.0-openjdk-1.8.0.265.b01-1.amzn2.0.1.x86_64/jre/lib/security/cacerts
/tmp/kafka.client.truststore.jks

wget https://archive.apache.org/dist/kafka/2.2.1/kafka_2.12-2.2.1.tgz

tar -xzvf kafka_2.12-2.2.1-bin.tar.gz

cd kafka_2.12-2.2.1
```

Desde el cliente podremos interactuar con el cluster, para crear topics o crear consumidores.

1. Ver estado del cluster y obtener las urls de Zookeeper. Necesitaremos el ARN del cluster. Con la respuesta nos fijaremos en la etiqueta *ZookeeperConnectString*

```bash
 $ aws kafka describe-cluster --region eu-central-1 —cluster-arn {ARN}

...
"ZookeeperConnectString": "z-1.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:2181,
z-3.emtclusterxxxxxx.c2.kafka.eu-central-1.amazonaws.com:2181,
z-2.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:2181"
...
```
2. Para crear los topics 
```bash
$ cd kafka_2.12-2.2.1

$ bin/kafka-topics.sh --create --zookeeper z-1.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:2181,z-3.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:2181, z-2.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:2181 --replication-factor 2 --partitions 1 --topic BiciMadGoRaw

$ bin/kafka-topics.sh --create --zookeeper z-1.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:2181,z-3.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:2181,z-2.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:2181 --replication-factor 2 --partitions 1 --topic BiciMadGoStage

```

```
3. Para obtener la url de los brokers
```bash
aws kafka get-bootstrap-brokers --region eu-central-1 --cluster-arn  {ARN}

...
{
    "BootstrapBrokerString": "b-1.emt-cluster.s9be9k.c2.kafka.eu-central-1.amazonaws.com:9092,b-2.emt-cluster.s9be9k.c2.kafka.eu-central-1.amazonaws.com:9092",
    "BootstrapBrokerStringTls": "b-1.emt-cluster.s9be9k.c2.kafka.eu-central-1.amazonaws.com:9094,b-2.emt-cluster.s9be9k.c2.kafka.eu-central-1.amazonaws.com:9094"
}
...

```

4. Para crear los consumidores y ver que se está escribiendo en los topics
```bash
$ bin/kafka-console-consumer.sh --bootstrap-server b-1.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:9092,b-2.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:9092 --topic BiciMadGoRaw —from-beginning

$ bin/kafka-console-consumer.sh --bootstrap-server b-1.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:9092,b-2.emtcluster.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:9092 --topic BiciMadGoStage --from-beginning
```

## Código Clúster EMR + Spark
Para la lectura del servicio MSK, hay que configurar un cluster EMR, caso sencillo un maestro *m4.large* y dos workers *m4.large*.

### Código
El código se ha desarrollado en el IDE IntelliJ, utilizando maven. En la carpeta [SparkEMR-MSK](https://github.com/JavieerCG/emt-streaming-etl/tree/main/SparkEMR-MSK) del repo se puede encontrar el pom.xml para importar el proyecto.
El proyecto se compone de 4 ficheros "útiles":

1. El fichero [EMTApp.scala](https://github.com/JavieerCG/emt-streaming-etl/blob/main/SparkEMR-MSK/src/main/scala/com.emt.etl/EMTApp.scala) es el main del proyecto. Que recibe un parámetro el cual será la lista de brokers de Kafka.

*Para configurar el consumidor de Kafka y crear un DStream*
```scala
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
```

*Parseo de los datos de entrada*
```scala
val bikes= stream.map[String](record => parseEMT(record.value()))
```

*Para crear el productor de Kafka (Spark no tiene una función propia)*
```scala
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
```
NOTA: haría falta modularizar el código y añadir tests
### JAR
El proyecto basta con compilarlo con maven para generar el JAR

```bash
mvn package
```

Este JAR se almacenará en un bucket se S3 s3://emt-code, al cual accederá el cluster de EMR para ejecutarlo. Puede hacerse desde un paso en el propio cluster o conectándonos al maestro.

```bash
#Paso de EMR
spark-submit --deploy-mode cluster --class com.emt.etl.EMTApp s3://emt-code/SparkEMR-MSK.jar b-1.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:9092,b-2.xxxxx.c2.kafka.eu-central-1.amazonaws.com:9092

#Maestro
/usr/lib/spark/bin/spark-submit --deploy-mode cluster SparkEMR-MSK.jar b-1.xxxxxx.c2.kafka.eu-central-1.amazonaws.com:9092,b-2.xxxxxxx.c2.kafka.eu-central-1.amazonaws.com:9092
```


# Elastic
Hay que crear un cluster en el servicio de Elasticsearch de Amazon, el cual nos proporcionará acceso a Elasticsearch a Kibana, con dos urls:
1. https://search-xxxxxxxxxxx.region.amazonaws.com 
2. https://search-xxxxxxxxxxx.region.amazonaws.com/_plugin/kibana

## Logstash
Debido a las características del proyecto hay que desplegar Logstash en una máquina EC2 *t2.small*.

### Instalación

```bash
sudo yum install java-1.8.0-openjdk 
JAVA_HOME=/usr/lib/jvm/jre-1.8.0-openjdk

rpm –import https://artifacts.elastic.co/GPG-KEY-elasticsearch

nano /etc/yum.repos.d/logstash.repo

sudo yum install logstash

mkdir settings
nano settings/logstash_user.conf

sudo /usr/share/logstash/bin/logstash -f settings/logstash-user.conf
```

El fichero [logstash-user.conf](https://github.com/JavieerCG/emt-streaming-etl/blob/main/logstash-config/logstash-user.conf) define el input, en nuestro caso un topic de Kafka y el output.
IMPORTANTE especificar la url de los brokers

El fichero [logstash.repo](https://github.com/JavieerCG/emt-streaming-etl/blob/main/logstash-config/logstash.repo)

## Kibana
Debido al requerimiento principal que es geoposicionar las bicicletas del servicio BiciMAD Go en el mapa de Madrid, se ha tenido que desplegar una máquina EC2 *t2.small* para poder tener acceso al fichero de configuración [kibana.yml](https://github.com/JavieerCG/emt-streaming-etl/blob/main/kibana-config/kibana.yml)

### Instalación
```bash
sudo yum install java-1.8.0-openjdk 
JAVA_HOME=/usr/lib/jvm/jre-1.8.0-openjdk

curl -O https://artifacts.elastic.co/downloads/kibana/kibana-oss-7.9.1-linux-x86_64.tar.gz
tar xvzf kibana-oss-7.9.1-linux-x86_64.tar.gz

cd kibana-7.9.1-linux-x86_64/

nano config/kibana.yml

bin/kibana
```

Según la configuración del fichero kibana.yml, la url de acceso a la interfaz será parecido a --> https://ec2-xxx-xxx-xxx-xxx.region.compute.amazonaws.com:5601

