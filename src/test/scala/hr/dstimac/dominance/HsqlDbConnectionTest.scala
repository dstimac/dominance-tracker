package hr.dstimac.dominance

import java.time.LocalDateTime

import hr.dstimac.dominance.db.DbConnection
import hr.dstimac.dominance.tracker.{Leaver, Online, Player}
import org.junit.Test

class HsqlDbConnectionTest {

  lazy val cfg: ApplicationConfig = ApplicationConfig.apply()
  var dbConn: DbConnection = DbConnection(cfg)

  @Test
  def insertPlayersTest(): Unit = {
    val p1 = Player("joza", Online, LocalDateTime.now())
    val p2 = Player("marko", Leaver, LocalDateTime.now())
    dbConn.logPlayerDiff(Set(p1, p2))


    val players = dbConn.findLastLogsByPlayer()
    players foreach println
  }
}
