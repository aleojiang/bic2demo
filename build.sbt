name := "bic2demo"

version := "1.0"

scalaVersion := "2.10.4"

//crossScalaVersions := Seq("2.10.4", "2.11.0")
//
//crossVersion := CrossVersion.binary

resolvers ++= {
  Seq(
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "spray repo" at "http://repo.spray.io"
  )
}

libraryDependencies ++= Seq(
//  "org.scalatest" %% "scalatest" % "2.1.3" % "test",
//  "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
//  "com.decodified" % "scala-ssh_2.10" % "0.6.4",
//  "com.beachape.filemanagement" %% "schwatcher" % "0.1.5",
//  "com.typesafe.akka" %% "akka-actor" % "2.3.2",
//  "com.netflix.rxjava" % "rxjava-scala" % "0.19.2",
  "joda-time" % "joda-time" % "2.1",
  "org.apache.spark" %% "spark-core" % "1.0.0",
  "org.apache.hadoop" % "hadoop-client" % "2.4.0" exclude("javax.servlet", "servlet-api")
    //exclude("javax.servlet", "servlet-api")
)


pomExtra :=
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


lazy val hello = taskKey[Unit]("Prints 'Hello World'")

hello := println("hello world!")