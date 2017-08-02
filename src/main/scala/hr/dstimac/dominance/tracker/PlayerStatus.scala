package hr.dstimac.dominance.tracker

sealed trait PlayerStatus
case object NewArrival extends PlayerStatus
case object Offline extends PlayerStatus
case object Online extends PlayerStatus
case object Leaver extends PlayerStatus