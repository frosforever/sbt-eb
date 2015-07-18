import com.typesafe.sbt.packager.docker._
import EbKeys._
import EB._


name         := "test-app"
version      := "2.1"
scalaVersion := "2.11.7"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaStreamV = "1.0"
  Seq(
    "com.typesafe.akka" %% "akka-http-experimental"         % akkaStreamV
  )
}

enablePlugins(JavaAppPackaging)

sourceDirectory in Docker := baseDirectory.value / "docker"

dockerBaseImage := "java:8"

maintainer in Docker := "frosforever"

dockerExposedPorts := Seq(9000)

//Manually building dockerFile to have better control and to ensure chmod is called before running
dockerCommands := Seq(
  Cmd("FROM", dockerBaseImage.value),
  Cmd("MAINTAINER", (maintainer in Docker).value),
  Cmd("EXPOSE", dockerExposedPorts.value.mkString(" ")),
  Cmd("ADD", {
    val files = (defaultLinuxInstallLocation in Docker).value.split(java.io.File.separator)(1)
    s"$files /$files"
  }),
  Cmd("WORKDIR", s"${(defaultLinuxInstallLocation in Docker).value}"),
  ExecCmd("RUN", "chown", "-R", (daemonUser in Docker).value, "."),
  ExecCmd("RUN", "chmod", "+x",
    s"${(defaultLinuxInstallLocation in Docker).value}/bin/${executableScriptName.value}"),
  Cmd("USER", (daemonUser in Docker).value),
  ExecCmd("ENTRYPOINT", s"bin/${name.value}"),
  ExecCmd("CMD")
)

ebSettings

ebS3BucketName := "us-west-1-test-bucket"

ebAppBundleSource := (stage in Docker).value

ebRegion := "us-west-1"

ebDescription := "hello"

ebAppName := "My First Elastic Beanstalk Application"

