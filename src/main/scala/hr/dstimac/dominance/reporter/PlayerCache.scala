package hr.dstimac.dominance.reporter

import hr.dstimac.dominance.tracker.Player
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

class PlayerCache {

  private val logger: Logger = LoggerFactory.getLogger(classOf[PlayerCache])

  protected var cache: mutable.SortedSet[Player] = mutable.SortedSet.empty[Player]
  protected var lastUpdateDiff: mutable.SortedSet[Player] = mutable.SortedSet.empty[Player]

  def update(players: mutable.SortedSet[Player]): Unit = {
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
      mutable.SortedSet.empty[Player] ++ changedPlayersData ++ newPlayersData
    } else players

    cache = players
    lastUpdateDiff = diffData
  }

  def getAll: mutable.SortedSet[Player] = cache
  def getDiff: mutable.SortedSet[Player] = lastUpdateDiff
}
