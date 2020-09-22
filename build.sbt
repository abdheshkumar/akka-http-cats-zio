val AkkaVersion = "2.6.8"
val AkkaHttpVersion = "10.2.0"
val CatsVersion = "2.2.0"
val ZIOVersion = "1.0.1"
lazy val root = (project in (file("."))).settings(
  name := "akka-http-cats-zio",
  version := "0.1",
  scalaVersion := "2.13.3",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "org.typelevel" %% "cats-core" % CatsVersion,
    "org.typelevel" %% "cats-effect" % CatsVersion,
    "org.typelevel" %% "cats-effect" % CatsVersion,
    "dev.zio" %% "zio" % ZIOVersion,
    "dev.zio" %% "zio-test" % ZIOVersion % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test
  )
)
