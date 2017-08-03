package hr.dstimac.dominance


import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import hr.dstimac.dominance.db.DbConnection
import hr.dstimac.dominance.reporter.{ConsoleReporter, PlayerCache, ReporterActor}
import hr.dstimac.dominance.tracker.{ElderTracker, OnlineTracker, Player}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.Duration

object Application extends App {

  private val logger = LoggerFactory.getLogger("hr.dstimac.dominance.Application")
  main()

  def main(): Unit = {
    logger.info("Starting Tracker")

    // fetch config
    val config = sys.props.get("app.conf") match {
      case Some(path) =>
        logger.debug("Loading config from path: {}", path)
        ApplicationConfig(path)
      case _ =>
        val conf = ApplicationConfig.apply()
        logger.debug("Loaded config: {}", conf.conf)
        conf
    }
    sys.props("webdriver.gecko.driver") = config.geckoDriverLocation

    // init dependencies
    val playerCache = new PlayerCache
    val dbConn = DbConnection(config)
    initPlayerCache(playerCache, dbConn)

    // init workers
    val system = ActorSystem("dominance-tracker")
    val reporters = Seq(new ConsoleReporter(playerCache, dbConn))
    val onlinePlayerTracker =
      system.actorOf(Props(new OnlineTracker(config, playerCache, dbConn)), "onlineTracker")
    val elderTracker =
      system.actorOf(Props(new ElderTracker(config, dbConn)), "elderTracker")
    val reporterActor =
      system.actorOf(Props(new ReporterActor(reporters)), "reporterActor")
    onlinePlayerTracker ! "start"

    attachShutdownHook(Seq(onlinePlayerTracker, elderTracker, reporterActor), dbConn)

    // start workers
    system.scheduler.schedule(
      Duration(10, TimeUnit.SECONDS)
      , Duration(config.resources.timeout, TimeUnit.MILLISECONDS)
      , onlinePlayerTracker
      , "log-presence"
    )
    system.scheduler.schedule(
      Duration(0, TimeUnit.SECONDS)
      , Duration(30, TimeUnit.SECONDS)
      , elderTracker
      , "log-elder"
    )
    system.scheduler.schedule(
      Duration(0, TimeUnit.SECONDS)
      , Duration(10, TimeUnit.SECONDS)
      , reporterActor
      , "report"
    )
  }

  private def initPlayerCache(cache: PlayerCache, dbConnection: DbConnection): Unit = {
    cache.update(mutable.SortedSet.empty[Player] ++ dbConnection.findLastLogsByPlayer())
  }

  private def attachShutdownHook(actors: Seq[ActorRef], dbConn: DbConnection): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        logger.info("Shutting down")
        dbConn.stopDb()
        actors.foreach{ a=> a ! PoisonPill}
      }
    })
  }
}
