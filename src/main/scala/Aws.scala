import java.io.File

import com.amazonaws._
import com.amazonaws.auth._
import com.amazonaws.event.{ProgressEvent, ProgressEventType, SyncProgressListener}
import com.amazonaws.regions.Regions
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient
import com.amazonaws.services.elasticbeanstalk.model.{CreateApplicationVersionRequest, DeleteApplicationVersionRequest, S3Location}
import com.amazonaws.services.s3._
import com.amazonaws.services.s3.model.PutObjectRequest

object ApplicationDeployer {
  def getEBClient(regionName: String) = {
    val credentials = new DefaultAWSCredentialsProviderChain
    val client = new AWSElasticBeanstalkClient(credentials, new ClientConfiguration().withProtocol(Protocol.HTTPS))
    //Using the Fluent `withRegion` throws ClassCastException: com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient cannot be cast to scala.runtime.Nothing$
    client.configureRegion(Regions.fromName(regionName))
    client
  }

  def deleteEbVersion(client: AWSElasticBeanstalkClient, applicationName: String, versionLabel: String) = {
    val request = new DeleteApplicationVersionRequest(applicationName, versionLabel).withDeleteSourceBundle(true)
    client.deleteApplicationVersion(request)
  }
  
  def createEbVersion(client: AWSElasticBeanstalkClient, applicationName: String, versionLabel: String, description: String, bundleS3Location: S3Location) = {
    //TODO: versionLabel between 1 and 100 chars without '/'. Should we modify it or just let it fail?
    val request = new CreateApplicationVersionRequest(applicationName, versionLabel).
      withSourceBundle(bundleS3Location).
      withDescription(description.take(199)) //Description max 200 chars
    client.createApplicationVersion(request)
  }
}


object BundleUploader {
  private def getS3Client = {
    val credentials = new DefaultAWSCredentialsProviderChain
    new AmazonS3Client(credentials, new ClientConfiguration().withProtocol(Protocol.HTTPS))
  }

  def uploadBundle(bucket: String, bundle: File): S3Location = {
    uploadBundle(bucket, bundle, bundle.getName)
  }

  def uploadBundle(bucket: String, bundle: File, key: String): S3Location = {
    val request = new PutObjectRequest(bucket, key, bundle)
    request.setGeneralProgressListener(UploadProgressListener(bundle.length()))
    getS3Client.putObject(request)
    new S3Location(bucket, key)
  }
  
  private def progressBar(percent:Int) = {
    val b="=================================================="
    val s="                                                 "
    val p=percent/2
    val z:StringBuilder=new StringBuilder(80)
    z.append("\r[")
    z.append(b.substring(0,p))
    if (p<50) {z.append(">"); z.append(s.substring(p))}
    z.append("]   ")
    if (p<5) z.append(" ")
    if (p<50) z.append(" ")
    z.append(percent)
    z.append("%   ")
    z.mkString
  }

  private case class UploadProgressListener(fileSize: Long) extends SyncProgressListener {
    var uploadedBytes = 0L

    override def progressChanged(progressEvent: ProgressEvent): Unit = {
      if (progressEvent.getEventType == ProgressEventType.REQUEST_BYTE_TRANSFER_EVENT ||
        progressEvent.getEventType == ProgressEventType.RESPONSE_BYTE_TRANSFER_EVENT) {
        uploadedBytes = uploadedBytes + progressEvent.getBytesTransferred
      }
      print(progressBar(if (fileSize > 0) ((uploadedBytes * 100) / fileSize).toInt else 100))
      if (progressEvent.getEventType == ProgressEventType.TRANSFER_COMPLETED_EVENT)
        println()
    }
  }
}