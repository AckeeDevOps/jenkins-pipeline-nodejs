def call(Map config) {
  def storageClass = config.documenation.storageClass ? storageClassconfig.documenation.storageClass : "regional"
  def location = config.documenation.location ? config.documenation.location : "europe-west3"
  sh(script: "gsutil mb -c ${storageClass} -l ${location} ${config.documentation.bucketUrl}")
}
