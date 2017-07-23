package com.bestmile
package service

import java.util

import akka.NotUsed
import model._

import scala.concurrent.Future
import persistence.{EventRepository, OnDiskEventRepository}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.kafka.scaladsl._
import akka.stream.scaladsl.Sink
import akka.stream._
import akka.stream.scaladsl._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.collection.JavaConverters._

class EventService(implicit appContext: AppContext) {
  implicit def ec = appContext.executionContext
  implicit def ma = appContext.actorMaterializer
  val eventRepository: EventRepository = new OnDiskEventRepository
  val topicsConfig = appContext.config.getConfig(("kafka.topics"))
  val bookingtopic = topicsConfig.getString("bookings")
  val vehiclestatustopic = topicsConfig.getString("vehicleStatus")
  val brokerConfig = appContext.config.getConfigList("kafka.brokers").asScala.toList.head
  val broker = s"${brokerConfig.getString("hostname")}:${brokerConfig.getInt("port")}"

  lazy val producerSettings = ProducerSettings(appContext.actorSystem,
    new StringSerializer, new StringSerializer).withBootstrapServers(broker)

  // TODO: save the offset in Postgres to achieve the “exactly once” Kafka semantic:
  // See "External Offset Storage" section here:
  /// http://doc.akka.io/docs/akka-stream-kafka/current/consumer.html
  lazy val consumerSettings = ConsumerSettings(appContext.actorSystem,
    new StringDeserializer, new StringDeserializer).withBootstrapServers(broker)
    .withGroupId("groupReporting").withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

  val readStream = buildReadStream

  def handleFakeKafka(request: AnyRef): Future[Boolean] = {
    val record = request match {
      case x: Booking => new ProducerRecord[String, String](bookingtopic, BookingEnvelope("command",x).asJson.noSpaces)
      case x: VehicleStatus => new ProducerRecord[String, String](vehiclestatustopic, x.asJson.noSpaces)
      case _ => sys.error("unexpected simulation request")
    }
    Source.single(record).runWith(Producer.plainSink(producerSettings)).map(_ => true)
  }

  def find(start: DateTime, end: DateTime): Future[Seq[Event]] = {
    eventRepository.find(start, end)
  }

  def summaryStats(start: DateTime, end: DateTime): Future[Seq[String]] = {
    val eventSource =
      Source.fromFuture(eventRepository.find(start, end))
        .mapConcat[Event](x => x.asInstanceOf[scala.collection.immutable.Iterable[Event]])

    val awtFlow = new UserWaitDetector().getFlow
        .fold((0.0, 0)){ (acc, uw) =>
          ( acc._1 + Duration(uw.booking.createdAt, uw.picked.createdAt).getMillis(),
            acc._2 + 1 ) }
        .map( acc => s"Average Wait Time, ${acc._1 / (acc._2 * 1000.0 * 60.0)} minutes\n" )

    val npFlow = new UserJourneyDetector().getFlow
          .fold((0,0)){ (acc, j) =>
            (acc._1 + j.dropped.seatCount, acc._2 + 1) }
          .map( acc => s"Average Number of Passengers, ${acc._1 / acc._2}\n" )

    val summarySource = Source.fromGraph(GraphDSL.create() {
      implicit builder: GraphDSL.Builder[NotUsed] =>
        import GraphDSL.Implicits._

        val split = builder.add(Broadcast[Event](2))
        val merge = builder.add(Merge[String](2))
        eventSource ~> split
                       split ~> awtFlow ~> merge
                       split ~> npFlow ~> merge
        SourceShape(merge.out)
      })

    summarySource.take(2)
      .prepend(Source.single(s"Start, ${DateTime.toISO(start)}\nEnd, ${DateTime.toISO(end)}\n"))
      .runWith(Sink.seq[String])
  }

  def journeyLog(start: DateTime, end: DateTime): Future[Seq[String]] = {
    val eventSource =
      Source.fromFuture(eventRepository.find(start, end))
        .mapConcat[Event](x => x.asInstanceOf[scala.collection.immutable.Iterable[Event]])

    val journeyLogSource = eventSource
      .via(new UserJourneyDetector().getFlow)
      .map(x => s"${x.created.createdAt},${x.picked.userID},${DateTime.toISO(x.picked.createdAt)},${DateTime.toISO(x.dropped.createdAt)},${x.dropped.seatCount},${x.picked.vehicleID}\n")

    journeyLogSource.runWith(Sink.seq[String])
  }

  def vehicleLog(start: DateTime, end: DateTime, interval: Duration): Future[Seq[VehicleAttrib]] = {
    val vehicleAttribSource =
      Source.fromFuture(eventRepository.find(start, end))
        .mapConcat[Event](x => x.asInstanceOf[scala.collection.immutable.Iterable[Event]])
        .collect{ case x:VehicleAttrib => x }

    val vehicleLogSource = vehicleAttribSource
      .via(new GroupByIntervalDetector[Vehicle.ID](interval, _.asInstanceOf[VehicleAttrib].vehicleID)
        .getFlow)
      .map( _.asInstanceOf[VehicleAttrib] )

    vehicleLogSource.runWith(Sink.seq[VehicleAttrib])
  }

  def deleteEvents: Future[Int] = eventRepository.deleteEvents

  private def buildReadStream: Future[akka.Done] = {
    Consumer.committableSource(consumerSettings, Subscriptions.topics(bookingtopic, vehiclestatustopic))
      .map { message =>
        val json = parse(message.record.value).getOrElse(Json.Null)
        val event = (json.as[BookingEnvelope], json.as[VehicleStatus]) match {
          case (Right(x), Left(y)) => Event(x)
          case (Left(x), Right(y)) => Event(y)
          case x => Seq(Ignore(DateTime.now))
        }
        (message, event)
      }
      .filterNot( _._2.head.isInstanceOf[Ignore] )
      .mapAsync(1) ( x => eventRepository.write(x._2).map(_ => x._1 ) )
      .mapAsync(1) { msg => msg.committableOffset.commitScaladsl() }
      .runWith(Sink.ignore)
  }
}
