package hr.dstimac.dominance.db

import akka.actor.Actor
import hr.dstimac.dominance.ApplicationConfig
import hr.dstimac.dominance.tracker.{ElderInfo, Player}
import org.slf4j.{Logger, LoggerFactory}

sealed trait DbActorAction
case object FindLastLogsByElder extends DbActorAction
case object FindLastLogsByPlayer extends DbActorAction
case object FindManaReportData extends DbActorAction

case class LogElderStatus(elderInfo: Set[ElderInfo])
case class LogPlayerDiff(playerDiff: Set[Player])

class DbActor(config: ApplicationConfig) extends Actor {

  private val logger: Logger = LoggerFactory.getLogger(classOf[DbActor])
  var dbConnection: DbConnection =_

  override def preStart(): Unit = {
    logger.info("Starting database...")
    dbConnection = DbConnection(config)
  }

  override def postStop(): Unit = {
    logger.info("Stopping database..")
    dbConnection.stopDb()
  }

  override def receive: Receive = {
    case LogElderStatus(es) => dbConnection.logElderStatus(es)
    case LogPlayerDiff(pd) => dbConnection.logPlayerDiff(pd)
    case FindLastLogsByElder => context.sender() ! dbConnection.findLastLogsByElder()
    case FindLastLogsByPlayer =>
      val players = dbConnection.findLastLogsByPlayer()
      logger.trace("FindLastLogByPlayer[{}]", players)
      context.sender() ! players
    case FindManaReportData => context.sender() ! dbConnection.findManaReportData()
    case _ => context.sender() ! "Unknown message"
  }
}

