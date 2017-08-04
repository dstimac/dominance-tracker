package hr.dstimac.dominance

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

case class LoginConf(
  username: String
  , password: String
  , usernameSelector: String
  , passwordSelector: String
  , loginButtonID: String
  , checkLogedInSelector: String
)
case class Resources(
  clientURL: String
 , timeout: Long
 , onlinePlayersSelector: String
 , errorFormSelector: String
 , statusPageURL: String
 , eldersSelector: String
)
case class Db(
  uri: String
  , serverInApp: Boolean
  , databaseServerType: String
  , username: String
  , password: String
 )
class ApplicationConfig(config: Config) {

  private val logger: Logger = LoggerFactory.getLogger(classOf[ApplicationConfig])

  lazy val conf: Config = {
    logger.trace("CONFIG: {}", config)
    config
  }
  lazy val trackerConfig: Config = {
    val c = conf.getConfig("dominance-tracker")
    logger.debug("Dominance tracker CONFIG: {}", c)
    c
  }


  val geckoDriverLocation: String = {
    trackerConfig.getString("web-driver-location") match {
      case "" =>
        // Default location
        sys.props("java.io.tmpdir") + "/dominance/geckodriver"
      case x => x
    }
  }

  def loginConf: LoginConf =
    LoginConf(trackerConfig.getString("login.username")
      , trackerConfig.getString("login.password")
      , trackerConfig.getString("login.username-input-selector")
      , trackerConfig.getString("login.password-input-selector")
      , trackerConfig.getString("login.button-login-id")
      , trackerConfig.getString("login.check-loggedin-selector")
    )

  def resources: Resources = {
    Resources(
      trackerConfig.getString("resources.client-url")
      , trackerConfig.getLong("resources.online-players-refresh-timeout")
      , trackerConfig.getString("resources.online-players-selector")
      , trackerConfig.getString("resources.error-forms")
      , trackerConfig.getString("resources.dominance-status-page")
      , trackerConfig.getString("resources.elder-elements-selector")
    )
  }

  def db: Db = {
    trackerConfig.getString("db.database-server-type") match {
      case x if x.nonEmpty =>
        Db(
          trackerConfig.getString("db.uri")
          , trackerConfig.getBoolean("db.server-mode-in-app")
          , x
          , trackerConfig.getString("db.username")
          , trackerConfig.getString("db.password")
        )
      case _ =>
        Db(
          trackerConfig.getString("db.uri")
          , trackerConfig.getBoolean("db.server-mode-in-app")
          , "hsqldb"
          , trackerConfig.getString("db.username")
          , trackerConfig.getString("db.password")
        )
    }
  }
}

object ApplicationConfig {
  def apply(filepath: String): ApplicationConfig = {
    new ApplicationConfig(ConfigFactory.parseFile(new File(filepath)))
  }

  def apply(): ApplicationConfig = new ApplicationConfig(ConfigFactory.load())
}
