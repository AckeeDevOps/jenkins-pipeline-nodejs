import groovy.json.*

def call(Map cfg, String branch, String build, String repositoryUrl = nil){
  def config = [:]

  // process simple stuff first
  config.workspace = pwd()
  config.startedBy = getNodeAuthorName() // last commit
  config.branch = branch
  config.buildNumber = build
  config.repositoryUrl = repositoryUrl
  
  // get short SHA hash
  dir('repo') {
    config.commitHash = sh(
      script: "git describe --always",
      returnStdout: true
    ).trim()
  }
  
  // app specific stuff
  config.projectFriendlyName = cfg.projectFriendlyName  
  config.appName = cfg.appName
  config.appRole = cfg.appRole
  config.appTier = cfg.appTier

  config.sshCredentialsId = cfg.sshCredentialsId
  config.slackChannel = cfg.slackChannel
  config.gitlabTagCredentials = cfg.gitlabTagCredentials // array
  
  // swagger/aglio documentation
  config.documentation = cfg.documentation

  // get default configuration for all branches
  config.envDefaults = cfg.envDefaults ? cfg.envDefaults : [:]
  
  // merge defaults with the actual values, actual values have always precedence
  config.envDetails = config.envDefaults + getNodeBranchConfig(cfg, config.branch)

  // get kubernetes config file path
  config.kubeConfigPath = "${config.envDetails.kubeConfigPathPrefix}/config-${config.envDetails.gcpProjectId}-${config.envDetails.gkeClusterName}"

  // get remote image tag
  config.dockerImageName = getNodeDockerImage(config)
  config.dockerImageTag = "${config.branch.replace("/", "-")}.${config.commitHash}"

  // apply some sanity checks
  validateEnvDetailsString('k8sNamespace', config)
  validateEnvDetailsString('friendlyEnvName', config)
  validateEnvDetailsString('gcpProjectId', config)
  validateEnvDetailsString('helmChart', config)
  validateEnvDetailsString('helmValues', config)
  validateEnvDetailsString('gkeClusterName', config)

  // check documenation values
  if(config.documenation) {
    if(!config.documenation.bucketUrl) { error(message: "documentation/buckerUrl must be set.") }
    if(!config.documenation.gcpProjectId) { error(message: "documentation/gcpProjectId must be set.") }
  }

  // prepare Helm release name here
  config.helmReleaseName = "${config.projectFriendlyName}-" +
    "${config.appName}-" +
    "${config.envDetails.friendlyEnvName}"

  // if needed, you can set helm release prefix
  if(config.envDetails.helmReleasePrefix) {
    config.helmReleaseName = config.envDetails.helmReleasePrefix + "-" + config.helmReleaseName
  }

  // set env variables
  env.CHANGELOG_PATH = "changelog.txt"
  env.SLACK_CHANNEL = config.slackChannel

  echo("end of parsing section")
  //return config object
  return config
}

def getNodeBranchConfig(Map cfg, branch) {
  def branchConfig = cfg.branchEnvs."${branch}"
  if(branchConfig) {
    return branchConfig
  } else {
    error(message: "Branch '${branch}' does not exist in the 'branchEnvs' Map.")
  }
}

def getNodeDockerImage(Map config) {

  // set default prefix - europe
  def gcrPrefix = config.envDetails.gcpDockerRegistryPrefix ?: "eu.gcr.io"

  image = "${gcrPrefix}/" +
    "${config.envDetails.gcpProjectId}/" +
    "${config.projectFriendlyName}/" +
    "${config.appName}"
  
  // image name should be converted to lowercase
  return image.toLowerCase()
}

def validateEnvDetailsString(String input, Map config) {
  if(!config.envDetails."${input}" || config.envDetails."${input}" == "") {
    error(message: "${input} has to be always set!")
  }
}
