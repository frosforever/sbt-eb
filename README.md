Sbt plugin to upload app version to elastic beanstalk.
This is very much a work in progress

# Set up

The following need to be set:

```
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

# TODO
- Make auto plugin
- Use version for `sbt-git` if active

# Acknowledgements

Project inspired by https://github.com/sqs/sbt-elasticbeanstalk and https://github.com/sbt/sbt-s3
