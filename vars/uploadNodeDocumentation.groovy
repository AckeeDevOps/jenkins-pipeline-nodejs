def call(Map config) {

  def pathPrefix
  if(config.documentation.pathPrefix) {
    pathPrefix = config.documentation.pathPrefix
  } else {
    pathPrefix = "${config.projectFriendlyName}-${config.appName}"
  }

  def command = "gsutil cp ./docs-output/index.html " +
    "${config.documentation.bucketUrl}/" +
    "${pathPrefix}/index.html"

  sh(script: command)
}
