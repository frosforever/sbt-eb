Sbt plugin to upload app version to elastic beanstalk.
This is very much a work in progress

# Set up
## General
The plugin uses the aws sdk's [DefaultAWSCredentialsProviderChain](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html) to read credentials from environment variables, or `~/.aws/credentials`. If you have the aws cli set up with permissions to the eb application and s3 bucket, you should be good to go.

## AWS
Though s3 is 'regionless', items are stored in a region. For some reason, elastic beanstalk applications can not access sources stored in s3 buckets outside their regions. There is no way (that I know of) to move an s3 bucket to a different region so be sure when it's created, it's in the same region as the application.

## Sbt
Install the plugin direct from source by adding the following to `project/plugins.sbt`:
```scala
lazy val ebPlugin = uri("git://github.com/frosforever/sbt-eb.git#v0.1")
```
The following need to be set in `build.sbt`:

```scala
ebSettings

ebS3BucketName := "bucket-to-store-app-version"

ebAppBundleSource := (stage in Docker).value // Source to bundle up

//Defaults to eu-west-1
ebRegion := "region"

ebDescription := "version description"

//Defaults to application name
ebAppName := "My First Elastic Beanstalk Application"

//Default to application version
ebVersion := "version"
```

### Potential issue
Archiving files can cause them to loose unix excutable permissions. The following should probably be added to `build.sbt` to fix it:
```scala
// Add `chmod` before RUN to fix executable permissions lost on zip
dockerCommands <<= (dockerCommands, dockerEntrypoint) map { (com, ent) =>
  com.flatMap {
    case e : ExecCmd if e.cmd == "ENTRYPOINT" =>
      val chmodCommand = Seq("chmod", "+x") ++ ent
      Seq(ExecCmd("RUN", chmodCommand: _*), e)
    case e => Seq(e)
  }
}
```
see https://frosforever.github.io/2015/10/26/docker-sbt-elastic-beanstalk.html for more details.

# Usage

`sbt ebCreateVersion` will zip the application source, upload it to the specified s3 bucket,
and create a new version for the elastic beanstalk application. It can then be deployed using
the AWS console or CLI.

# TODO
- Make auto plugin
- Use version for `sbt-git` if active
- Allow seperate settings for extensions

## Description from sbt-git
The idea is to have this built in as part of the auto-plugin but for now the following manual settings will work. 
Add the sbt-git plugin in `plugins.sbt`:
```
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")
```
in `build.sbt` add the following:
```scala
// set version for git sha
enablePlugins(GitVersioning)

// Sets the eb version to the git sha with timestamp
ebVersion := {
  def timestamp(time: Long): String = {
    // no delimiter between date & time in order to not break rpm versioning rules
    val sdf = new java.text.SimpleDateFormat("YYYYMMdd'T'HHmmss'Z'")
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf.format(new Date(time))
  }

  (timestamp(System.currentTimeMillis()) + "-" + version.value).take(100)
}

// Set eb description to the git head commit message
ebDescription := {
  val subject = {
    val prefix = if (git.gitUncommittedChanges.value) {
      git.uncommittedSignifier.value.map(_ + " - ")
    } else { None }

    prefix.getOrElse("") + logMessageTask.value
  }

  subject.take(200)
}

lazy val logMessageTask = taskKey[String]("extracting the head log message")

logMessageTask := {
  val runner = git.runner.value
  val dir = baseDirectory.value
  val gitArgs = Seq("log", "-1", """--pretty=%s""")
  runner(gitArgs: _*)(dir, com.typesafe.sbt.git.NullLogger)
}
```

## Extensions
Currently any extensions and the `Dockerrun.aws.json` file must be added to the source bundle. If using `sbt-native-pacakger` to docker, they can be placed directly in a `docker` directory and will be picked up in `docker:stage`. Otherwise they need to be added in `build.sbt`. Hopefully this can be handled by the plugin someday.

# Acknowledgements

Project inspired by https://github.com/sqs/sbt-elasticbeanstalk and https://github.com/sbt/sbt-s3
