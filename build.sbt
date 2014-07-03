name := "bic2demo"

version := "1.0"

scalaVersion := "2.11.0"

crossScalaVersions := Seq("2.10.4", "2.11.0")

crossVersion := CrossVersion.binary

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.1.3" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.3.2",
  "com.netflix.rxjava" % "rxjava-scala" % "0.19.2",
  "com.beachape.filemanagement" %% "schwatcher" % "0.1.5"
)

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies += "com.decodified" % "scala-ssh_2.10" % "0.6.4"

pomExtra := (
  <url>https://github.com/aleojiang/tempwork.git</url>
    <scm>
      <url>git@github.com:aleojiang/tempwork.git</url>
      <connection>scm:git:git@github.com:aleojiang/tempwork.git</connection>
    </scm>
    <developers>
      <developer>
        <id>aleojiang</id>
        <name>aleojiang</name>
        <url></url>
      </developer>
    </developers>
  )
