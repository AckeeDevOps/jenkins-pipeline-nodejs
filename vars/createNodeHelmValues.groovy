import groovy.json.*

def call(Map config, Map secrets) {

  def values = config.helmValues

  //general section
  values.general = [:]
  values.general.appName = config.appName
  values.general.projectName = config.projectFriendlyName
  values.general.appRole = config.appRole
  values.general.appTier = config.appTier
  values.general.envName = config.envDetails.friendlyEnvName
  values.general.imageTag = config.dockerImageTag

  // build specific info
  values.general.meta = [:]
  values.general.meta.builtBy = config.startedBy
  values.general.meta.buildNumber = config.buildNumber
  values.general.meta.branch = config.branch
  values.general.meta.repositoryUrl = config.repositoryUrl

  //secretsSection
  if(config.secretsInjection) {
    values.secrets = [:]
    values.secrets.data = secrets
  }

  return JsonOutput.toJson(values)
}
