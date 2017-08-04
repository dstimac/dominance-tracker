package hr.dstimac.dominance.db

import java.util.concurrent.TimeUnit

import akka.actor.SupervisorStrategy.Escalate
import akka.actor.{Actor, OneForOneStrategy, SupervisorStrategy}
import hr.dstimac.dominance.ApplicationConfig
import hr.dstimac.dominance.tracker.{ElderInfo, Player}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

sealed trait DbActorAction
case object FindLastLogsByElder extends DbActorAction
case object FindLastLogsByPlayer extends DbActorAction
case object FindManaReportData extends DbActorAction

case class LogElderStatus(elderInfo: Set[ElderInfo])
case class LogPlayerDiff(playerDiff: Set[Player])

class DbActor(config: ApplicationConfig) extends Actor {

  private val logger: Logger = LoggerFactory.getLogger(classOf[DbActor])
  var dbConnection: Option[DbConnection] = None

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = Duration(2, TimeUnit.SECONDS)) {
      case _: Throwable  => Escalate
    }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    message match {
      case Some(msg) =>
        logger.error("Restarting with message: {}, because of: ", msg, reason)
      case _ =>
        logger.error("Restarting because of: ", reason)
    }
  }

  override def postStop(): Unit = {
    logger.info("Stopping database..")
    dbConnection.foreach(_.stopDb())
  }

  def running: Receive = {
    case LogElderStatus(es) => dbConnection.foreach(_.logElderStatus(es))
    case LogPlayerDiff(pd) =>
      logger.trace("Logging player diff: {}", pd)
      dbConnection.foreach(_.logPlayerDiff(pd))
    case FindLastLogsByElder => context.sender() ! dbConnection.get.findLastLogsByElder()
    case FindLastLogsByPlayer =>
    val players = dbConnection.get.findLastLogsByPlayer()
    logger.trace("FindLastLogByPlayer[{}]", players)
    context.sender() ! players
    case FindManaReportData => context.sender() ! dbConnection.get.findManaReportData()
    case _ => context.sender() ! "Unknown message"
  }

  override def receive: Receive = {
    case "start" =>
      val resp = Try { Some(DbConnection(config)) } match {
        case Success(dbConnOpt) =>
          dbConnection = dbConnOpt
          context.become(running)
          "started"
        case Failure(t) =>
          logger.error("Error while trying to start dbActor: ", t)
          "failed"
      }
      sender() ! resp
  }
}

