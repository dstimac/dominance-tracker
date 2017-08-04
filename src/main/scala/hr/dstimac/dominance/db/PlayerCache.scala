package hr.dstimac.dominance
package db

import java.util.concurrent.TimeUnit

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorRef, OneForOneStrategy, SupervisorStrategy}
import akka.pattern.{AskTimeoutException, ask}
import hr.dstimac.dominance.tracker.Player
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.Duration

trait PlayerCacheAction
case object GetAll extends  PlayerCacheAction
case object GetDiff extends PlayerCacheAction
case class UpdatePlayers(players: Set[Player]) extends PlayerCacheAction

class PlayerCache(dbActor: ActorRef) extends Actor {

  private val logger: Logger = LoggerFactory.getLogger(classOf[PlayerCache])

  protected var cache: Set[Player] = Set.empty[Player]
  protected var lastUpdateDiff: Set[Player] = Set.empty[Player]

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = Duration(1, TimeUnit.SECONDS)) {
    case e: AskTimeoutException =>
      logger.warn("AskTimeoutException in PlayerCache: ", e)
      Restart
    case _: Throwable => Stop
  }

  override def preStart(): Unit = {
    logger.info("Initializing cache...")
    (dbActor ? FindLastLogsByPlayer)(Duration(10, TimeUnit.SECONDS)).mapTo[Set[Player]] map {players =>
      cache = players
      logger.trace("New cache = {}", cache)
    }
  }

  override def receive: Receive = {
    case UpdatePlayers(p) =>
      logger.trace("Updating cahce with {}", p)
      update(p)
    case GetAll =>
      logger.trace("Getall from cache: {}", cache)
      context.sender() ! cache
    case GetDiff =>
      logger.trace("GetDiff from cache {}", lastUpdateDiff)
      context.sender() ! lastUpdateDiff
    case _ => context.sender() ! "Unknown message"
  }

  private def update(players: Set[Player]): Unit = {
    val diffData = if(cache.nonEmpty) {
      val changedPlayersData = players.flatMap { reportPlayer =>
        cache.find(_.name == reportPlayer.name) match {
          case Some(p) if p.status != reportPlayer.status => Some(reportPlayer)
          case _ => None
        }
      }
      logger.trace("ChangedPlayersData: {}", changedPlayersData)
      val newPlayersData = players.filterNot(p => cache.exists(r => r.name == p.name))
      logger.trace("NewPlayersData: {}", newPlayersData)
      changedPlayersData ++ newPlayersData
    } else players

    cache = players
    lastUpdateDiff = diffData
  }
}
