name := "infiniteneo4jdemo"

resolvers += "codahale" at "http://repo.codahale.com/"

resolvers += "anormcypher" at "http://repo.anormcypher.org/"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

scalaVersion := "2.9.2"

libraryDependencies ++= Seq(
    "io.spray" % "spray-client" % "1.0-M6",
    "org.anormcypher" %% "anormcypher" % "0.2.2",
    "com.typesafe" % "config" % "1.0.0",
    "com.typesafe.akka" % "akka-actor" % "2.0.4"
)

mainClass in (Compile, run) := Some("infiniteneo4jdemo.Transfer")
