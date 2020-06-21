name := "akka-tracing-demo"
organization := "com.amdelamar"
version := "1.0"
scalaVersion := "2.13.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.5",
  "com.typesafe.akka" %% "akka-stream" % "2.6.5",
  "com.typesafe.akka" %% "akka-http" % "10.1.11",
  "com.typesafe.play" %% "play-json" % "2.7.4",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.github.blemale" %% "scaffeine" % "3.1.0",
  "io.kamon" %% "kamon-core" % "2.1.0",
  "io.kamon" %% "kamon-status-page" % "2.1.0",
  "io.kamon" %% "kamon-akka-http" % "2.1.0",
  "io.kamon" %% "kamon-zipkin" % "2.1.0"
)

javaAgents += "io.kamon" % "kanela-agent" % "1.0.5"

lazy val root = (project in file("."))
  .settings(
    fork in run := true
  )
  .enablePlugins(JavaAppPackaging, JavaAgent)
