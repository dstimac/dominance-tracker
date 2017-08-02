package hr.dstimac.dominance.reporter

import akka.actor.Actor

class ReporterActor(reporters: Seq[Reporter]) extends Actor {
  override def receive: Receive = {
    case "report" => reporters.foreach(_.report())
    case _ => sender() ! "unknown message"
  }
}
