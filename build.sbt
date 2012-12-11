name := "infiniteneo4jdemo"

resolvers += "codahale" at "http://repo.codahale.com/"

resolvers += "anormcypher" at "http://repo.anormcypher.org/"

scalaVersion := "2.9.2"

libraryDependencies ++= Seq(
    "com.roundeights" % "hasher" % "0.3" from "http://cloud.github.com/downloads/Nycto/Hasher/hasher_2.9.1-0.3.jar",
    "commons-codec" % "commons-codec" % "1.4",
    "net.databinder.dispatch" %% "dispatch-core" % "0.9.4",
    "org.anormcypher" %% "anormcypher" % "0.2.2",
    "org.streum" %% "configrity-core" % "0.10.2"
)

mainClass in (Compile, run) := Some("infiniteneo4jdemo.Transfer")
