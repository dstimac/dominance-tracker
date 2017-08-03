package hr.dstimac.dominance.tracker

import java.time.LocalDateTime

import akka.actor.{Actor, ActorRef}
import hr.dstimac.dominance.ApplicationConfig
import hr.dstimac.dominance.db.LogElderStatus
import org.jsoup.Jsoup
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

case class ElderInfo(name: String, omni: Int, dominionSize: Int, createdAt: LocalDateTime)
class ElderTracker(config: ApplicationConfig, dbActor: ActorRef) extends Actor {

  private val logger: Logger = LoggerFactory.getLogger(classOf[ElderTracker])
  // Not all present but enough
  val ranks = Seq("Noble", "Master", "Ruler", "Colossus", "Daar", "Imdi", "Ghan")

  override def receive: Receive = {
    case "log-elder" =>
      logger.debug("Logging elder data")
      logOmni()
    case _ =>
      sender() ! "unknown message"
  }

  private def logOmni(): Unit = {
    val elderData = fetchElderData()
    logger.trace("Elder Data: {}", elderData)
    dbActor ! LogElderStatus(elderData)
  }

  private def fetchElderData(): Set[ElderInfo] = {
    val doc = Jsoup.connect(config.resources.statusPageURL).get()
    logger.trace("StatusPage Content: {}", doc.toString)
    val elems = doc.select(config.resources.eldersSelector).asScala
    elems.map { e =>
      ElderInfo(
        name = cleanRank(e.select("td:eq(1)").text())
        , omni = e.select("td:eq(3)").text().trim.toInt
        , dominionSize = e.select("td:eq(4)").text().trim.toInt
        , LocalDateTime.now()
      )
    }.toSet
  }

  private def cleanRank(s: String): String = {
    ranks.foldLeft(s){ (step, r) => step.replaceAll(r, "") }.trim
  }
}
