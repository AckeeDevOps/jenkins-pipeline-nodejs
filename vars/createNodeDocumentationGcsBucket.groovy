def call(Map config) {
  def storageClass = config.documenation.storageClass ? config.documenation.storageClass : "regional"
  def location = config.documenation.location ? config.documenation.location : "europe-west3"

  def command = "gsutil mb -c ${storageClass} " +
    "-l ${location} " +
    "-p ${config.documentation.gcpProjectId} " +
    "${config.documentation.bucketUrl}"

  sh(script: command)
}
