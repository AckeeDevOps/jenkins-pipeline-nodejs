def call(Map config) {

  def pathPrefix
  if(config.documentation.pathPrefix) {
    pathPrefix = config.documentation.pathPrefix
  } else {
    pathPrefix = "${config.projectFriendlyName}-${config.appName}"
  }

  def command = "gsutil cp -r ./docs-output/ " +
    "${config.documentation.bucketUrl}/" +
    "${pathPrefix}/${config.branch}/"

  sh(script: command)
}
