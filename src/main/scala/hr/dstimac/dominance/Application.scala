package hr.dstimac.dominance


import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import hr.dstimac.dominance.db.{DbActor, PlayerCache}
import hr.dstimac.dominance.reporter.{ConsoleReporter, ReporterActor}
import hr.dstimac.dominance.tracker.{ElderTracker, OnlineTracker}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration

object Application extends App {

  private val logger = LoggerFactory.getLogger("hr.dstimac.dominance.Application")
  main()

  def main(): Unit = {
    logger.info("Starting Tracker")

    // fetch config
    val config = sys.props.get("app.conf") match {
      case Some(path) =>
        logger.info("Loading config from path: {}", path)
        ApplicationConfig(path)
      case _ =>
        val conf = ApplicationConfig.apply()
        logger.debug("Loaded config: {}", conf.conf)
        conf
    }
    sys.props("webdriver.gecko.driver") = config.geckoDriverLocation

    // init workers
    val system = ActorSystem("dominance-tracker")
    val dbActor = system.actorOf(Props(new DbActor(config)), "dbActor")
    val cacheActor = system.actorOf(Props(new PlayerCache(dbActor)), "playerCache")

//    initPlayerCache(dbActor, cacheActor)

    val reporters = Seq(new ConsoleReporter(cacheActor, dbActor))
    val onlinePlayerTracker =
      system.actorOf(Props(new OnlineTracker(config, cacheActor, dbActor)), "onlineTracker")
    val elderTracker =
      system.actorOf(Props(new ElderTracker(config, dbActor)), "elderTracker")
    val reporterActor =
      system.actorOf(Props(new ReporterActor(reporters)), "reporterActor")
    onlinePlayerTracker ! "start"

    // start workers
    logger.debug("Starting workers...")
    val onlinePresenceScheduler = system.scheduler.schedule(
      Duration(10, TimeUnit.SECONDS)
      , Duration(config.resources.timeout, TimeUnit.MILLISECONDS)
      , onlinePlayerTracker
      , "log-presence"
    )
    val elderTrackerScheduler = system.scheduler.schedule(
      Duration(0, TimeUnit.SECONDS)
      , Duration(30, TimeUnit.SECONDS)
      , elderTracker
      , "log-elder"
    )
    val reporterScheduler = system.scheduler.schedule(
      Duration(0, TimeUnit.SECONDS)
      , Duration(10, TimeUnit.SECONDS)
      , reporterActor
      , "report"
    )

    logger.debug("Attaching shutdown hooks")
    attachShutdownHook(
      Seq(onlinePresenceScheduler, elderTrackerScheduler, reporterScheduler)
      , Seq(onlinePlayerTracker, elderTracker, reporterActor, cacheActor, dbActor)
    )
  }

  private def attachShutdownHook(schedulers: Seq[Cancellable], actors: Seq[ActorRef]): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        logger.info("Shutting down")
        schedulers.foreach{s => s.cancel()}
        actors.foreach{ a => a ! PoisonPill}
      }
    })
  }
}
