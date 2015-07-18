
name := "sbt-eb"

description := "Plugin for elastic beanstalk uploads"

version := "0.1"

organization := "com.frosforever"

sbtPlugin := true

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % "1.10.5.1",
  "com.amazonaws" % "aws-java-sdk-elasticbeanstalk" % "1.10.5.1"
)

