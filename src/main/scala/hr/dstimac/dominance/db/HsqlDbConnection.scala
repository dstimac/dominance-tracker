package hr.dstimac.dominance.db

import java.sql.{Connection, DriverManager, Timestamp}
import java.time.LocalDateTime

import org.hsqldb.persist.HsqlProperties
import org.hsqldb.server.Server
import hr.dstimac.dominance.ApplicationConfig
import hr.dstimac.dominance.tracker._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class PlayerStatusOmniData(name: String
  , status: PlayerStatus
  , lastStatusChange: LocalDateTime
  , omni: Int
  , dominionSize: Int
  , lastOmniUptate: LocalDateTime
)
trait DbConnection {
  private[db] def init(): Unit
  def stopDb(): Unit
  def logElderStatus(elders: Set[ElderInfo]): Unit
  def logPlayerDiff(players: Set[Player]): Unit
  def findLastLogsByElder(): Set[ElderInfo]
  def findLastLogsByPlayer(): Set[Player]
  def findManaReportData(): Set[PlayerStatusOmniData]
}
class HsqlDbConnection(config: ApplicationConfig) extends DbConnection{

  private val logger: Logger = LoggerFactory.getLogger(classOf[HsqlDbConnection])
  private var server: Option[Server] = None
  private var conn: Connection = _

  def stopDb(): Unit = {
    server.map(_.stop())
  }

  private[db] def init(): Unit = {
    if(config.db.serverInApp) {
      val p = new HsqlProperties()

      p.setProperty("server.database.0", "file:" + config.db.uri)
      p.setProperty("server.dbname.0", "onliner")
      p.setProperty("server.port", "9001")

      val srv = new Server()
      srv.setProperties(p)
      srv.setLogWriter(null) // can use custom writer
      srv.setErrWriter(null)
      srv.start()

      srv.checkRunning(true)
      server = Some(srv)
    }
    setupConnection()
    setupPlayerLog()
    setupElderLog()
  }

  private def setupConnection(): Unit = {
    Class.forName("org.hsqldb.jdbcDriver")
    conn = DriverManager.getConnection("jdbc:hsqldb:hsql://localhost/onliner", "SA", "")
  }

  private def setupPlayerLog(): Unit = {
    val st = conn.createStatement()
    val qry = """SELECT *
                |FROM INFORMATION_SCHEMA.TABLES
                |WHERE TABLE_SCHEMA = 'PUBLIC'
                |  AND TABLE_NAME = 'PLAYER_LOG';""".stripMargin
    val rs = st.executeQuery(qry)
    val hasTablePlayerLog = rs.next()
    rs.close()
    st.close()

    if(!hasTablePlayerLog) {
      logger.info("Creating table 'player_log'.")
      val st = conn.createStatement()
      val qry =
        """CREATE TABLE player_log
          |(name VARCHAR(255) not NULL,
          |status VARCHAR(20) not NULL,
          |created_at timestamp not NULL)
        """.stripMargin
      st.executeUpdate(qry)
      st.close()
    }
  }

  private def setupElderLog(): Unit = {
    val st = conn.createStatement()
    val qry = """SELECT *
                |FROM INFORMATION_SCHEMA.TABLES
                |WHERE TABLE_SCHEMA = 'PUBLIC'
                |  AND TABLE_NAME = 'ELDER_LOG';""".stripMargin
    val rs = st.executeQuery(qry)
    val hasTableOmniLog = rs.next()
    rs.close()
    st.close()

    if(!hasTableOmniLog) {
      logger.info("Creating table 'elder_log'.")
      val st = conn.createStatement()
      val qry =
        """CREATE TABLE elder_log
          |(name VARCHAR(255) not NULL,
          |omni int not NULL,
          |dominion_size int not NULL,
          |created_at timestamp not NULL)
        """.stripMargin
      st.executeUpdate(qry)
      st.close()
    }
  }

  def logElderStatus(elders: Set[ElderInfo]): Unit = {
    if(elders.nonEmpty) {
      val qry = """INSERT INTO elder_log(name, omni, dominion_size, created_at) VALUES (?, ?, ?, ?)"""
      val st = conn.prepareStatement(qry)
      elders.foreach { p =>
        st.setString(1, p.name)
        st.setInt(2, p.omni)
        st.setInt(3, p.dominionSize)
        st.setTimestamp(4, Timestamp.valueOf(p.createdAt))
        st.addBatch()
      }
      st.executeBatch()
      st.close()
      conn.commit()
    }
  }

  def logPlayerDiff(players: Set[Player]): Unit = {
    if(players.nonEmpty) {
      val qry = """INSERT INTO player_log(name, status, created_at) VALUES (?, ?, ?)"""
      val st = conn.prepareStatement(qry)
      players.foreach { p =>
        val dbStatus = p.status match {
          case NewArrival => "online"
          case Online => "online"
          case Offline => "offline"
          case Leaver => "offline"
        }
        st.setString(1, p.name)
        st.setString(2, dbStatus)
        st.setTimestamp(3, Timestamp.valueOf(p.lastChange))
        st.addBatch()
      }
      st.executeBatch()
      st.close()
      conn.commit()
    }
  }

  def findLastLogsByElder(): Set[ElderInfo] = {
    val qry = """SELECT DISTINCT e.*
                |FROM elder_log e INNER JOIN
                |    (SELECT name, MAX(created_at) as max_time
                |     FROM elder_log
                |     GROUP BY name) grouped
                |ON e.name = grouped.name
                |AND e.created_at = grouped.max_time""".stripMargin
    val st = conn.createStatement()
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
                |FROM player_log p INNER JOIN
                |    (SELECT name, MAX(created_at) as max_time
                |     FROM player_log
                |     GROUP BY name) grouped
                |ON p.name = grouped.name
                |AND p.created_at = grouped.max_time""".stripMargin
    val st = conn.createStatement()
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
                |FROM player_log p
                |    INNER JOIN (
                |        SELECT name, MAX(created_at) as max_time
                |        FROM player_log
                |        GROUP BY name) grouped
                |        ON p.name = grouped.name
                |    INNER JOIN (
                |        SELECT DISTINCT e.*
                |        FROM elder_log e
                |        INNER JOIN (
                |            SELECT name, MAX(created_at) as max_time
                |            FROM elder_log
                |            GROUP BY name) grouped
                |            ON e.name = grouped.name
                |        AND e.created_at = grouped.max_time) elders
                |ON p.name = elders.name
                |AND p.created_at = grouped.max_time
                |ORDER BY omni desc""".stripMargin
    val st = conn.createStatement()
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

object DbConnection {
  def apply(config: ApplicationConfig): DbConnection = {
    val dbc = config.db.databaseServerType match {
      //case "hsqldb" => new HsqlDbConnection(config)
      case "postgres" => new PostgresDbConnection(config)
      case _ => new HsqlDbConnection(config)
    }
    dbc.init()
    dbc
  }
}
