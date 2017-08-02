package hr.dstimac.dominance.reporter

import hr.dstimac.dominance.db.DbConnection
import org.slf4j.{Logger, LoggerFactory}


class HsqldbReporter(dbConn: DbConnection, val cache: PlayerCache) extends Reporter {

  override val logger: Logger = LoggerFactory.getLogger(classOf[HsqldbReporter])

  override def report(): Unit = {
//    dbConn.logPlayerDiff(cache.getDiff.toSeq)
  }
}

object HsqldbReporter {
  def apply(dbConn: DbConnection, cache: PlayerCache): Reporter = {
    val reporter = new HsqldbReporter(dbConn, cache)
    reporter
  }
}
