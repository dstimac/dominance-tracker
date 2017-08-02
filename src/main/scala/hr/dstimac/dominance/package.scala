package hr.dstimac

import java.util.concurrent.Executors

import hr.dstimac.dominance.tracker.Player

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

package object dominance {

  implicit val ec: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  implicit val playerOrdering: Ordering[Player] = new Ordering[Player] {
    override def compare(x: Player, y: Player): Int = {
      x.name.compareTo(y.name)
    }
  }

}
