import java.io.File

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient
import com.amazonaws.services.elasticbeanstalk.model.{ApplicationVersionDescription, S3Location}
import sbt.{SettingKey, TaskKey}

object EbKeys {
  //TODO: Investigate adding an `isSnapshot` key and overwriting existing snapshots if it is to allow repeated uploads. Would require a delete app version task

  //User set
  val ebS3BucketName = SettingKey[String]("ebS3BucketName", "S3 bucket which should contain uploaded zip files")

  //User set. If possible default to `stage in Docker`
  //TODO: Support multiple files. That way you get the source as well as the .ebextensions. Or have that as an optional other setting
  val ebAppBundleSource = TaskKey[File]("eb-app-bundle-source", "Source to be zipped to a deployable app bundle")

  //User set
  val ebRegion = SettingKey[String]("ebRegion", "Elastic Beanstalk region (e.g., us-west-1)")

  val ebAppBundle = TaskKey[File]("eb-app-bundle", "The application file ('source bundle' in AWS terms) to deploy.")

  val ebCreateVersion = TaskKey[ApplicationVersionDescription]("eb-create-version", "Creates a new application version in the configured environment.")

  val ebUploadSourceBundle = TaskKey[S3Location]("eb-upload-source-bundle", "Uploads the WAR source bundle to S3")

  val ebClient = TaskKey[AWSElasticBeanstalkClient]("eb-client")

  val ebStageTarget = SettingKey[File]("ebStageTarget", "location of eb staging target")

  val ebVersion = TaskKey[String]("ebVersion", "version label and appBundle name to be used ")

  val ebAppName = SettingKey[String]("ebAppName", "Name of the application on elastic beanstalk")

  val ebDescription = TaskKey[String]("eb-description", "description of created version")
}

//TODO: AutoPlugin that if native packager is enabled sets the bundleSource. And if git-sbt is enabled, sets the version and description
trait EbSettings { this: EbTasks =>
  import EbKeys._
  import sbt.Keys.{name, target, version}
  import sbt._

  lazy val ebSettings = Seq[Setting[_]](
    ebCreateVersion <<= ebCreateVersionTask,
    ebUploadSourceBundle <<= ebUploadSourceBundleTask,
    ebAppBundle <<= ebAppBundleTask,
    ebClient <<= ebRegion map { (region) => ApplicationDeployer.getEBClient(region) },
    ebVersion := version.value,
    ebStageTarget := (target.value  / "eb"),
    ebRegion := "eu-west-1",
    ebAppName := name.value
  )
}

trait EbTasks {
  import sbt.IO
  import sbt.Keys.streams

  val ebAppBundleTask = (EbKeys.ebStageTarget, EbKeys.ebAppBundleSource, EbKeys.ebVersion, streams) map {
    (staging, docTar, versionLabel, s) =>
      val zipFile = new File(staging, s"$versionLabel.zip")
      s.log.info(s"Cleaning elastic-beanstalk staging directory $staging")

      IO.delete(staging)

      s.log.info(s"Writing elastic-beanstalk app bundle to $zipFile")

      def entries(f: File):List[File] = f :: (if (f.isDirectory) IO.listFiles(f).toList.flatMap(entries) else Nil)
      IO.zip(entries(docTar).collect{case d if d.getAbsolutePath != docTar.getAbsolutePath => (d, d.getAbsolutePath.substring(docTar.getAbsolutePath.length +1))}, zipFile)
      zipFile
  }

  val ebUploadSourceBundleTask = (EbKeys.ebAppBundle, EbKeys.ebS3BucketName, EbKeys.ebAppName, streams) map {
    (appBundle, s3BucketName, appName, s) => {
      require(appBundle.getName.endsWith("zip"), "App bundle must be a zip archive")

      s.log.info("Uploading " + appBundle.getName + " (" + (appBundle.length/1024/1024) + " MB) " +
        "to Amazon S3 bucket '" + s3BucketName + "'")
      BundleUploader.uploadBundle(s3BucketName, appBundle, s"$appName/${appBundle.getName}")
    }
  }

  val ebCreateVersionTask = (EbKeys.ebClient, EbKeys.ebUploadSourceBundle, EbKeys.ebAppName, EbKeys.ebVersion, EbKeys.ebDescription, streams) map {
    (ebClient, ebSourceBundle,  appName, versionLabel, description, s) =>

      s.log.info(s"Creating application: $appName version: $versionLabel description: $description from s3: $ebSourceBundle")
      ApplicationDeployer.createEbVersion(ebClient, appName, versionLabel, description, ebSourceBundle).
        getApplicationVersion
  }
}
