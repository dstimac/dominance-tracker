package hr.dstimac.dominance.db

import java.sql.{Connection, DriverManager, Timestamp}

import hr.dstimac.dominance.ApplicationConfig
import hr.dstimac.dominance.tracker._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class PostgresDbConnection(config: ApplicationConfig) extends DbConnection {

  private val logger: Logger = LoggerFactory.getLogger(classOf[PostgresDbConnection])
  private var connection: Connection = _

  override private[db] def init() = {
    // "jdbc:postgresql://localhost/test?user=fred&password=secret&ssl=true"
    val url = s"${config.db.uri}?user=${config.db.username}&password=${config.db.password}"
    connection = DriverManager.getConnection(url)
    setUpSchema()
    setUpTables()
    setUpIndexes()
  }

  override def stopDb(): Unit = ()

  private def setUpIndexes(): Unit = {
    logger.info("Creating indexes")
    val indexQuerries = Seq(
      "CREATE INDEX IF NOT EXISTS player_log_created_at_idx ON tracker.player_log USING btree (created_at)"
      , "CREATE INDEX IF NOT EXISTS player_log_name_idx ON tracker.player_log USING btree (name COLLATE pg_catalog.\"default\")"
    )

    indexQuerries.foreach {qry =>
      val st = connection.createStatement()
      st.executeUpdate(qry)
      st.close()
    }
  }

  private def setUpTables(): Unit = {
    if(!checkTableExists("player_log")) {
      logger.info("Creating table 'player_log'")
      val qry =
        """
          |CREATE TABLE tracker.player_log(
          |  name VARCHAR(255) NOT NULL
          |  , status VARCHAR(20) NOT NULL
          |  , created_at TIMESTAMP NOT NULL
          |)""".stripMargin
      val st = connection.createStatement()
      st.executeUpdate(qry)
      st.close()
    }
    if(!checkTableExists("elder_log")) {
      logger.info("Creating table 'elder_log'")
      val qry =
        """
          |CREATE TABLE tracker.elder_log(
          |  name VARCHAR(255) NOT NULL
          |  , omni INTEGER NOT NULL
          |  , dominion_size INTEGER NOT NULL
          |  , created_at TIMESTAMP NOT NULL
          |)
        """.stripMargin
      val st = connection.createStatement()
      st.executeUpdate(qry)
      st.close()
    }
  }

  private def setUpSchema(): Unit = {
    val qry = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'tracker'"
    val st = connection.createStatement()

    val rs = st.executeQuery(qry)
    if(!rs.next()) {
      logger.info("Creating schema 'tracker'")
      val qry = "CREATE SCHEMA tracker"
      val st = connection.createStatement()
      st.executeUpdate(qry)
      st.close()
      rs.close()
    }
    rs.close()
    st.close()
  }

  private def checkTableExists(tableName: String): Boolean = {
    val qry =
      """
        |SELECT table_name
        |FROM   information_schema.tables
        |WHERE  table_schema = 'tracker'
        |AND    table_name = ?
      """.stripMargin
    val st = connection.prepareStatement(qry)

    st.setString(1, tableName)
    val rs = st.executeQuery()
    val hasTable = rs.next()
    rs.close()
    st.close()
    hasTable
  }

  override def logElderStatus(elders: Set[ElderInfo]): Unit = {
    val qry = "INSERT INTO tracker.elder_log(name, omni, dominion_size, created_at) VALUES(?, ?, ?, ?)"
    val st = connection.prepareStatement(qry)
    elders.foreach { e =>
      st.setString(1, e.name)
      st.setInt(2, e.omni)
      st.setInt(3, e.dominionSize)
      st.setTimestamp(4, Timestamp.valueOf(e.createdAt))
      st.addBatch()
    }
    st.executeBatch()
    st.close()
  }

  override def logPlayerDiff(players: Set[Player]): Unit = {
    val qry = "INSERT INTO tracker.player_log(name, status, created_at) VALUES(?, ?, ?)"
    val st = connection.prepareStatement(qry)
    players.foreach { p =>
      val status = p.status match {
        case Online | NewArrival => "online"
        case _ => "offline"
      }
      st.setString(1, p.name)
      st.setString(2, status)
      st.setTimestamp(3, Timestamp.valueOf(p.lastChange))
      st.addBatch()
    }
    st.executeBatch()
    st.close()
  }

  def findLastLogsByElder(): Set[ElderInfo] = {
    val qry = """
             SELECT DISTINCT e.*
             |FROM tracker.elder_log e INNER JOIN
             |    (SELECT name, MAX(created_at) as max_time
             |     FROM tracker.elder_log
             |     GROUP BY name) grouped
             |ON e.name = grouped.name
             |AND e.created_at = grouped.max_time""".stripMargin
    val st = connection.createStatement()
    val rs = st.executeQuery(qry)
    val lastLogs: mutable.Buffer[ElderInfo] = new ListBuffer[ElderInfo]


    while(rs.next()) {
      val p = ElderInfo(
        name = rs.getString("name")
        , omni =  rs.getInt("omni")
        , dominionSize = rs.getInt("dominion_size")
        , rs.getTimestamp("created_at").toLocalDateTime)
      lastLogs.append(p)
    }
    rs.close()
    st.close()

    lastLogs.toSet
  }

  def findLastLogsByPlayer(): Set[Player] = {
    val qry = """SELECT DISTINCT p. *
                |FROM tracker.player_log p INNER JOIN
                |    (SELECT name, MAX(created_at) as max_time
                |     FROM tracker.player_log
                |     GROUP BY name) grouped
                |ON p.name = grouped.name
                |AND p.created_at = grouped.max_time""".stripMargin
    val st = connection.createStatement()
    val rs = st.executeQuery(qry)
    val lastLogs: mutable.Buffer[Player] = new ListBuffer[Player]


    while(rs.next()) {
      val status = rs.getString("status") match {
        case "online" => Online
        case _ => Offline
      }
      val p = Player(name = rs.getString("name"), status, rs.getTimestamp("created_at").toLocalDateTime)
      lastLogs.append(p)
    }
    rs.close()
    st.close()

    lastLogs.toSet
  }

  def findManaReportData(): Set[PlayerStatusOmniData] = {
    val qry = """SELECT DISTINCT
                |  p.name
                |  , p.status
                |  , p.created_at last_status_change
                |  , elders.omni
                |  , elders.dominion_size
                |  , elders.created_at last_omni_update
                |FROM tracker.player_log p
                |    INNER JOIN (
                |        SELECT name, MAX(created_at) as max_time
                |        FROM tracker.player_log
                |        GROUP BY name) grouped
                |        ON p.name = grouped.name
                |    INNER JOIN (
                |        SELECT DISTINCT e.*
                |        FROM tracker.elder_log e
                |        INNER JOIN (
                |            SELECT name, MAX(created_at) as max_time
                |            FROM tracker.elder_log
                |            GROUP BY name) grouped
                |            ON e.name = grouped.name
                |        AND e.created_at = grouped.max_time) elders
                |ON p.name = elders.name
                |AND p.created_at = grouped.max_time
                |ORDER BY omni desc""".stripMargin
    val st = connection.createStatement()
    val rs = st.executeQuery(qry)
    val lastLogs: mutable.Buffer[PlayerStatusOmniData] = new ListBuffer[PlayerStatusOmniData]

    while(rs.next()) {
      val status = rs.getString("status") match {
        case "online" => Online
        case _ => Offline
      }
      val p = PlayerStatusOmniData(
        name = rs.getString("name")
        , status = status
        , lastStatusChange = rs.getTimestamp("last_status_change").toLocalDateTime
        , omni = rs.getInt("omni")
        , dominionSize = rs.getInt("dominion_size")
        , lastOmniUptate = rs.getTimestamp("last_omni_update").toLocalDateTime
      )
      lastLogs.append(p)
    }
    rs.close()
    st.close()

    lastLogs.toSet
  }
}
