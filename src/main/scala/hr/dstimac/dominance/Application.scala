package hr.dstimac.dominance

import akka.actor.{ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import akka.pattern.ask
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

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

    // init app actor
    val system = ActorSystem("dominance-tracker")
    val applicationActor = system.actorOf(Props(new ApplicationActor(config)), "applicationActor")

    val startFuture = (applicationActor ? "start").mapTo[String]
    startFuture.onComplete{
      case Success("failed") =>
        logger.warn("ApplicationActor not started, exiting.")
        sys.exit(-1)
      case Success("started") =>
        logger.info("ApplicationActor started")
        applicationActor ! "run"
      case Success(x) =>
        logger.warn("Unknown response from ApplicationActor: '{}'", x)
      case Failure(t) =>
        applicationActor ! "stop"
        logger.warn("Application not started: ", t)
    }

    logger.debug("Attaching shutdown hooks")
    attachShutdownHook(
      Seq()
      , Seq(applicationActor)
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
