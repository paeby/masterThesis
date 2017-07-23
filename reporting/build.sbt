name          := "reporting"
organization  := "com.bestmile"
version       := "1.0.0-SNAPSHOT"
scalaVersion  := "2.12.0"

scalacOptions ++= Seq("-feature","-deprecation", "-Xlint:_", "-Ywarn-unused", "-Xfatal-warnings", "-language:postfixOps")

enablePlugins(JavaAppPackaging)
packageName in Universal := s"reporting_${version.value}"

// First try to fetch libs from our Nexus Proxy
resolvers += "BestMile Public (proxy)" at "http://nexus.int.bestmile.com/content/groups/public/"
// If you are out the office/vpn then fetch from central
resolvers += "Maven repo" at "http://repo.maven.apache.org/maven2"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases"
resolvers += Resolver.bintrayRepo("hseeberger", "maven")

val akkaHttpVersion = "10.0.1"
val circeVersion = "0.6.1"
val enumeratumVersion = "1.5.4"
val jtsVersion        = "1.14.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.scalaz" %% "scalaz-core" % "7.2.8",

  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.11.0",

  "com.beachape" %% "enumeratum" % enumeratumVersion,
  "com.beachape" %% "enumeratum-circe" % enumeratumVersion,

  "com.eaio.uuid" % "uuid" % "3.2",
  "joda-time" % "joda-time" % "2.9.7",

  "com.typesafe.slick" %% "slick" % "3.2.0-M2",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "net.logstash.logback" % "logstash-logback-encoder" % "4.8",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.0-M2",
  "org.postgresql" % "postgresql" % "9.4.1212",
  "com.github.tminglei" %% "slick-pg" % "0.15.0-M3",
  "com.github.tminglei" %% "slick-pg_circe-json" % "0.15.0-M3",

  "com.typesafe.akka" %% "akka-stream-kafka" % "0.13",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.16",

  "com.vividsolutions" % "jts-core" % jtsVersion
)


// sbt publish -> snapshots/releases to our private repositories
publishTo := {
  val nexus = "http://nexus.int.bestmile.com/"
  if (isSnapshot.value)
    Some("BestMile Snapshots" at nexus + "content/repositories/snapshots/")
  else
    Some("BestMile Releases"  at nexus + "content/repositories/releases/")
}
