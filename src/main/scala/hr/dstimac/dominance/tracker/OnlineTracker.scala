package hr.dstimac.dominance
package tracker

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, OneForOneStrategy, SupervisorStrategy}
import akka.pattern.ask
import hr.dstimac.dominance.db.{GetAll, LogPlayerDiff, UpdatePlayers}
import org.openqa.selenium._
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.FluentWait
import org.openqa.selenium.support.ui.ExpectedConditions._
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration

case class Player(name: String, status: PlayerStatus, lastChange: LocalDateTime)

class OnlineTracker(config: ApplicationConfig, playerCache: ActorRef, dbActor: ActorRef) extends Actor {

  private val logger = LoggerFactory.getLogger(classOf[OnlineTracker])

  private var driver: RemoteWebDriver = _

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    logger.debug("Restarting tracker: Error[{}]", reason.getMessage)
    super.preRestart(reason, message)
  }

  override def postStop(): Unit = {
    logger.debug("Cleaning up")
    try {
      driver.quit()
    } catch {
      case x: Exception =>
        logger.error("Exception while trying to cleanup: ", x)
    }
  }

  override def preStart(): Unit = {
    driver = new FirefoxDriver()
  }

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = Duration(2, TimeUnit.SECONDS)) {
      case x: StaleElementReferenceException =>
        logger.warn("Caught exception, trying to resume: ", x)
        Resume
      case _: Exception => Restart
      case _: Throwable => Stop
    }

  override def receive: Receive = {
    case "start" =>
      login()
      context.become(log)
    case "log-presence" =>
      sender() ! "not ready"
    case _ =>
      sender() ! "unknown message"
  }

  def log: Receive = {
    case "log-presence" =>
      logPresence()
    case _ =>
      sender() ! "unknown message"
  }

  @tailrec
  final def login(): Unit = {
    logger.debug("Logging into client...")
    driver.get(config.resources.clientURL)
    Thread.sleep(2000)

    if(checkLoggedIn()) {
      // Already logged in
    }
    else {
      val buttonLogIn = new FluentWait(driver)
        .withTimeout(5, TimeUnit.SECONDS)
        .pollingEvery(250, TimeUnit.MILLISECONDS)
        .until(presenceOfElementLocated(By.id(config.loginConf.loginButtonID)))

      driver.findElement(By.xpath(config.loginConf.usernameSelector)).sendKeys(config.loginConf.username)
      driver.findElement(By.xpath(config.loginConf.passwordSelector)).sendKeys(config.loginConf.password)
      buttonLogIn.click()

//      Thread.sleep(3000)

      if(checkLoggedIn()) logger.debug("Logged in...")
      else login()
    }
  }

  private def checkLoggedIn(): Boolean = {
    new FluentWait[WebDriver](driver)
      .withTimeout(15, TimeUnit.SECONDS)
      .pollingEvery(500, TimeUnit.MILLISECONDS)
      .ignoring(classOf[NoSuchElementException])
      .until(or(
        presenceOfAllElementsLocatedBy(By.xpath(config.resources.onlinePlayersSelector)),
        presenceOfAllElementsLocatedBy(By.xpath(config.resources.errorFormSelector))
      ))

    val errorPopUpExists = driver.findElements(By.xpath(config.resources.errorFormSelector))
      .asScala
      .exists(_.isDisplayed)
    val loginButtonIsPresent = driver.findElements(By.id(config.loginConf.loginButtonID))
      .asScala
      .exists(_.isDisplayed)
    val loggedIn = driver.findElements(By.xpath(config.loginConf.checkLogedInSelector))
      .asScala
      .exists(_.isDisplayed)

    (errorPopUpExists, loginButtonIsPresent, loggedIn) match {
      case (true, _, _) =>
        logger.trace("not logged in error popup present")
        false
      case (_, true, _) =>
        logger.trace("not logged in still on login form")
        false
      case (_, _, true) =>
        logger.trace("success, logged in")
        true
      case _ =>
        logger.trace("not logged in")
        false
    }
  }

  private def logPresence(): Unit = {
    logger.debug("Logging online presence ...")

    Thread.sleep(config.resources.timeout)

    if(!checkLoggedIn()) login()

    val onlinePlayers = driver.findElements(By.xpath(config.resources.onlinePlayersSelector))
      .asScala.map(_.getAttribute("textContent")).toList

    val newArrivals: mutable.Buffer[Player] = new ListBuffer[Player]
    val leavers: mutable.Buffer[Player] = new ListBuffer[Player]
    val residents: mutable.Buffer[Player] = new ListBuffer[Player]
    val offline: mutable.Buffer[Player] = new ListBuffer[Player]

    // Sort out known players, update status, lastChange
    val cacheResultFuture = (playerCache ? GetAll).mapTo[Set[Player]]
    cacheResultFuture.map { cache =>
      cache.foreach { p =>
        if (onlinePlayers.contains(p.name)) {
          p.status match {
            case Offline =>
              residents.append(p.copy(lastChange = LocalDateTime.now(), status = NewArrival))
            case Leaver =>
              residents.append(p.copy(lastChange = LocalDateTime.now(), status = NewArrival))
            case Online =>
              residents.append(p)
            case NewArrival =>
              residents.append(p.copy(status = Online))
          }
        }
        else {
          if (p.status == Online || p.status == NewArrival)
            leavers.append(p.copy(lastChange = LocalDateTime.now(), status = Leaver))
          else
            offline.append(p.copy(status = Offline))
        }

        // Sort out new arrivals (unknown players)
        onlinePlayers.filterNot { p => cache.exists(c => c.name == p) }.foreach { name =>
          newArrivals.append(Player(name, NewArrival, LocalDateTime.now))
        }

        val players = newArrivals ++ leavers ++ residents ++ offline filterNot (_.name.trim.isEmpty)
        // update cache
        playerCache ! UpdatePlayers(players.toSet)

        logger.trace("FOUND PLAYERS: {}", cache)
        dbActor ! LogPlayerDiff(cache)
      }
    }
 }
}
