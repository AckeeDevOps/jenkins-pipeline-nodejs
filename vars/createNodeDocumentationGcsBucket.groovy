def call(Map config) {
  def storageClass = config.documentation.storageClass ? config.documentation.storageClass : "regional"
  def location = config.documentation.location ? config.documentation.location : "europe-west3"

  def command = "gsutil mb -c ${storageClass} " +
    "-l ${location} " +
    "-p ${config.documentation.gcpProjectId} " +
    "${config.documentation.bucketUrl}"

  sh(script: command)
}
