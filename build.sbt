/* base settings */
organization := "hr.dstimac"
name         := "dominance-tracker"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.seleniumhq.selenium" % "selenium-java" % "3.4.0"

  , "com.typesafe.akka" % "akka-actor_2.11" % "2.5.3"
  , "com.typesafe.akka" % "akka-slf4j_2.11" % "2.5.3"
  , "com.typesafe.akka" % "akka-testkit_2.11" % "2.5.3" % "test"

  , "org.jsoup" % "jsoup" % "1.10.3"

  , "org.hsqldb" % "hsqldb" % "2.4.0"
  , "org.postgresql" % "postgresql" % "42.1.3"

  , "ch.qos.logback" % "logback-classic" % "1.2.3"

  , "junit" % "junit" % "4.12" % "test"
)

enablePlugins(JavaAppPackaging)
