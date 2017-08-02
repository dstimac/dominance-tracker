package hr.dstimac.dominance

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

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
case class Db(path: String, serverInApp: Boolean)
class ApplicationConfig(config: Config) {

  lazy val conf: Config = config
  lazy val onlinerConf: Config = conf.getConfig("dominance-tracker")


  val geckoDriverLocation: String = {
    onlinerConf.getString("web-driver-location") match {
      case "" =>
        // Default location
        sys.props("java.io.tmpdir") + "/dominance/geckodriver"
      case x => x
    }
  }

  def loginConf: LoginConf =
    LoginConf(onlinerConf.getString("login.username")
      , onlinerConf.getString("login.password")
      , onlinerConf.getString("login.username-input-selector")
      , onlinerConf.getString("login.password-input-selector")
      , onlinerConf.getString("login.button-login-id")
      , onlinerConf.getString("login.check-loggedin-selector")
    )

  def resources: Resources = {
    Resources(
      onlinerConf.getString("resources.client-url")
      , onlinerConf.getLong("resources.online-players-refresh-timeout")
      , onlinerConf.getString("resources.online-players-selector")
      , onlinerConf.getString("resources.error-forms")
      , onlinerConf.getString("resources.dominance-status-page")
      , onlinerConf.getString("resources.elder-elements-selector")
    )
  }

  def db: Db = {
    Db(onlinerConf.getString("db.path"), onlinerConf.getBoolean("db.server-mode-in-app"))
  }
}

object ApplicationConfig {
  def apply(filepath: String): ApplicationConfig = {
    new ApplicationConfig(ConfigFactory.parseFile(new File(filepath)))
  }

  def apply(): ApplicationConfig = new ApplicationConfig(ConfigFactory.load())
}
