def call(Map config) {
  def storageClass = config.documentation.storageClass ? config.documentation.storageClass : "regional"
  def location = config.documentation.location ? config.documentation.location : "europe-west3"

  def bucketExists = sh(script: "gsutil ls -p ${config.documentation.gcpProjectId} | grep ${config.documentation.bucketUrl}", returnStatus: true)
  
  if(bucketExists != 0){
    def command = "gsutil mb -c ${storageClass} " +
      "-l ${location} " +
      "-p ${config.documentation.gcpProjectId} " +
      "${config.documentation.bucketUrl}"
    sh(script: command)
  } else {
    echo("Bucket ${config.documentation.bucketUrl} (project: ${config.documentation.gcpProjectId}) already exists.")
  }
}
