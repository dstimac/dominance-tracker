package hr.dstimac.dominance

import java.util.concurrent.TimeUnit

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, Props, SupervisorStrategy}
import akka.pattern.ask
import hr.dstimac.dominance.db.{DbActor, PlayerCache}
import hr.dstimac.dominance.reporter.{ConsoleReporter, ReporterActor}
import hr.dstimac.dominance.tracker.{ElderTracker, OnlineTracker}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ApplicationActor(config: ApplicationConfig) extends Actor {

  private val logger: Logger = LoggerFactory.getLogger(classOf[ApplicationActor])
  private var schedulers: Seq[Cancellable] = Seq.empty
  private var dbActor: ActorRef = _
  private var cacheActor: ActorRef = _

  override def postStop(): Unit = {
    schedulers.foreach(_.cancel())
    context.children.foreach { c =>
      context.unwatch(c)
      context.stop(c)
    }
  }

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = Duration(2, TimeUnit.SECONDS)) {
      case _: Throwable  => Stop
  }

  def started: Receive = {
    case "stop" => context.stop(self)
    case "run" => run()
    case _ => sender() ! "Unknown message"
  }

  override def receive: Receive = {
    case "start" =>
      dbActor = context.actorOf(Props(new DbActor(config)), "dbActor")
      val dbActorStartFuture = (dbActor ? "start").mapTo[String]
      val startResponse = Await.result(dbActorStartFuture, Duration(2, TimeUnit.SECONDS)) match {
        case "failed" =>
          context.stop(self)
          "failed"
        case _ =>
          cacheActor = context.actorOf(Props(new PlayerCache(dbActor)), "playerCache")
          context.become(started)
          "started"
      }

      sender() ! startResponse
    case _ =>
  }

  def run(): Unit = {
    val reporters = Seq(new ConsoleReporter(cacheActor, dbActor))
    val onlinePlayerTracker =
      context.actorOf(Props(new OnlineTracker(config, cacheActor, dbActor)), "onlineTracker")
    val elderTracker =
      context.actorOf(Props(new ElderTracker(config, dbActor)), "elderTracker")
    val reporterActor =
      context.actorOf(Props(new ReporterActor(reporters)), "reporterActor")
    onlinePlayerTracker ! "start"

    // start workers
    logger.debug("Starting workers...")
    val onlinePresenceScheduler = context.system.scheduler.schedule(
      Duration(10, TimeUnit.SECONDS)
      , Duration(config.resources.timeout, TimeUnit.MILLISECONDS)
      , onlinePlayerTracker
      , "log-presence"
    )
    val elderTrackerScheduler = context.system.scheduler.schedule(
      Duration(0, TimeUnit.SECONDS)
      , Duration(30, TimeUnit.SECONDS)
      , elderTracker
      , "log-elder"
    )
    val reporterScheduler = context.system.scheduler.schedule(
      Duration(0, TimeUnit.SECONDS)
      , Duration(10, TimeUnit.SECONDS)
      , reporterActor
      , "report"
    )

    schedulers = Seq(onlinePresenceScheduler, elderTrackerScheduler, reporterScheduler)

  }
}
