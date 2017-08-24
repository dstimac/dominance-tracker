package hr.dstimac.dominance
package reporter

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import akka.actor.ActorRef
import akka.pattern.ask
import hr.dstimac.dominance.db.{FindManaReportData, GetAll, PlayerStatusOmniData}
import hr.dstimac.dominance.tracker._
import org.slf4j.{Logger, LoggerFactory}

trait Reporter {
  protected val logger: Logger
  def report(): Unit
}

case class PlayerReportData(
  name: String
  , status: PlayerStatus
  , lastChange: LocalDateTime
  , ticksOffline: Int
  , manaPerTick: Option[Int]
  , omni: Option[Int]
  , manaEstimate: Option[Int]
)

class ConsoleReporter(cache: ActorRef, dbActor: ActorRef) extends Reporter {

  override protected val logger: Logger = LoggerFactory.getLogger(classOf[ConsoleReporter])
  protected val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM HH:mm:ss")

  def report(): Unit = {
    val reportDataFuture = (dbActor ? FindManaReportData).mapTo[Set[PlayerStatusOmniData]] flatMap { manaReportData  =>
      val offlinePlayerManaReportData = manaReportData.filter(_.status == Offline)
      (cache ? GetAll).mapTo[Set[Player]] map { cache =>
        cache.map{p =>
            offlinePlayerManaReportData.find(_.name == p.name) match {
              case Some(mrd) =>
                val ticksOffline = (ChronoUnit.MINUTES.between(p.lastChange, mrd.lastOmniUptate) / 30).toInt
                if(ticksOffline < -30) {
                  // ReportData too old or player no longer on /status page, skip it for now
                  PlayerReportData(p.name, p.status, p.lastChange, ticksOffline * -1, None, None, None)
                } else {
                  val manaPerTick = (math.sqrt(mrd.omni) + 10).toInt
                  PlayerReportData(p.name
                    , p.status
                    , p.lastChange
                    , math.abs(ticksOffline)
                    , Some(manaPerTick)
                    , Some(mrd.omni)
                    , Some(manaPerTick * math.abs(ticksOffline)))
                }
              case _ =>
                val ticksOffline = p.status match {
                  case Offline | Leaver =>
                    (ChronoUnit.MINUTES.between(p.lastChange, LocalDateTime.now()) / 30).toInt
                  case _ =>
                    0
                }
                PlayerReportData(p.name, p.status, p.lastChange, ticksOffline, None, None, None)
            }
        }

      }
    }

    val report: StringBuilder = new StringBuilder
    report.append("\nPlayer Report: \n")
    val header =
      String.format("     %20s %15s %10s %10s %10s %10s\n"
        , "Elder Name"
        , "Last change"
        , "Omni"
        , "Mana"
        , "Offline"
        , "Income")
    report.append(header)
    reportDataFuture map { rd =>
      rd.foreach { p =>
        val omni = p.omni.getOrElse("N/A").toString
        val mana = p.manaEstimate.getOrElse("N/A").toString
        val lastChange = p.lastChange.format(timeFormat)
        val ticksOffline = p.ticksOffline.toString
        val income = p.manaPerTick.map(_.toString).getOrElse("N/A")
        val playerReport = p.status match {
          case NewArrival =>
            String.format(" >>> %20s %15s %10s %10s %10s %10s", p.name, lastChange, omni, mana, ticksOffline, income)
          case Offline =>
            String.format(" --- %20s %15s %10s %10s %10s %10s", p.name, lastChange, omni, mana, ticksOffline, income)
          case Leaver =>
            String.format(" <<< %20s %15s %10s %10s %10s %10s", p.name, lastChange, omni, mana, ticksOffline, income)
          case Online =>
            String.format("     %20s %15s %10s %10s %10s %10s", p.name, lastChange, omni, mana, ticksOffline, income)
        }
        report.append(playerReport).append("\n")
      }
      logger.info(report.toString())
    }
  }
}
