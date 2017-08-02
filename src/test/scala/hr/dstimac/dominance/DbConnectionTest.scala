package hr.dstimac.dominance

import java.time.LocalDateTime

import hr.dstimac.dominance.db.DbConnection
import hr.dstimac.dominance.tracker.{Leaver, Online, Player}
import org.junit.Test

class DbConnectionTest {

  lazy val cfg: ApplicationConfig = ApplicationConfig.apply()
  var dbConn: DbConnection = DbConnection(cfg)


  @Test
  def testDbLocation(): Unit = {
    val c = dbConn.connection()
    val st = c.createStatement()
    val qry = "SELECT * FROM player_log LIMIT 1"
    val rs = st.executeQuery(qry)

    while(rs.next()) {
      val name = rs.getString("name")
      val status = rs.getString("status")
      val ts = rs.getTimestamp("created_at")
      println(s" player = ($name, $status, ${ts.toLocalDateTime})")
    }

    st.close()
  }

  @Test
  def insertPlayersTest(): Unit = {
    val p1 = Player("joza", Online, LocalDateTime.now())
    val p2 = Player("marko", Leaver, LocalDateTime.now())
    dbConn.logPlayerDiff(Seq(p1, p2))


    val players = dbConn.findLastLogsByPlayer()
    players foreach println
  }
}
