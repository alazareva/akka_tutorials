name := "akka_tutorials"

version := "0.1"

scalaVersion := "2.12.7"

lazy val akkaVersion = "2.5.19"
lazy val scalaTestVersion = "3.0.5"

lazy val leveldbVersion = "0.7"
lazy val leveldbjniVersion = "1.8"
lazy val postgresVersion = "42.2.2"
lazy val cassandraVersion = "0.91"
lazy val json4sVersion = "3.2.11"
lazy val protobufVersion = "3.6.1"
lazy val akkaHttpVersion = "10.1.7"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion,

  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,

  // local levelDB stores
  "org.iq80.leveldb" % "leveldb" % leveldbVersion,
  "org.fusesource.leveldbjni" % "leveldbjni-all" % leveldbjniVersion,

  // JDBC with PostgreSQL 
  "org.postgresql" % "postgresql" % postgresVersion,
  "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.4.0",

  // Cassandra
  "com.typesafe.akka" %% "akka-persistence-cassandra" % cassandraVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % cassandraVersion % Test,

  // Google Protocol Buffers
  "com.google.protobuf" % "protobuf-java"  % protobufVersion,

  // akka http
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,

  // JWT
  "com.pauldijou" %% "jwt-spray-json" % "2.1.0",
  
  // clustering 
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion
)
