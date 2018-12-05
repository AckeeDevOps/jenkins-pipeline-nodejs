import groovy.json.*

def call(Map cfg, String branch, String build, String repositoryUrl = nil){
  def config = [:]

  // process simple stuff first
  config.workspace = pwd()
  config.branch = branch
  config.buildNumber = build
  config.repositoryUrl = repositoryUrl
  config.projectFriendlyName = cfg.projectFriendlyName
  config.appName = cfg.appName
  config.appRole = cfg.appRole
  config.appTier = cfg.appTier
  config.dryRun = cfg.dryRun ?: false
  config.startedBy = getAuthorName()
  config.dryRun = cfg.dryRun ?: false
  config.kubeConfigPathPrefix = cfg.kubeConfigPathPrefix
  config.gcpDockerRegistryPrefix = cfg.gcpDockerRegistryPrefix
  config.sshCredentialsId = cfg.sshCredentialsId
  config.debugMode = cfg.debugMode ? cfg.debugMode : false
  config.slackChannel = cfg.slackChannel

  // process more complex stuff
  config.envDetails = getNodeBranchConfig(cfg, config.branch)
  config.kubeConfigPath = getNodeKubeConfigPath(cfg, config.envDetails)
  config.dockerImageTag = getNodeDockerTag(cfg, config.envDetails, config.branch, config.buildNumber)
  config.secretsInjection = config.envDetails.secretsInjection
  config.helmValues = getNodeHelmValues(config.envDetails)
  config.helmChart = getNodeHelmChart(config.envDetails)
  config.testConfig = cfg.testConfig
  config.documentation = cfg.documentation
  
  // process envDetails dry run if specified
  if(config.envDetails.dryRun) { config.dryRun = config.envDetails.dryRun }

  // apply some sanity checks
  validateEnvDetailsString('k8sNamespace', config)
  validateEnvDetailsString('friendlyEnvName', config)
  validateEnvDetailsString('gcpProjectId', config)
  validateEnvDetailsString('helmChart', config)

  // check values of configuration objects if they exist
  // secrets injection part
  echo("Validating secretsInjection for the current env.")
  if(config.secretsInjection) { validateSecretsInjection(config.secretsInjection) }
  echo("Validating secretsInjection for the test env.")
  if(config.testConfig) {
    if(config.testConfig.secretsInjection) { validateSecretsInjection(config.testConfig.secretsInjection) }
  }

  // check documenation values
  if(config.documenation) {
    if(!config.documenation.bucketUrl) { error(message: "documentation/buckerUrl must be set.") }
    if(!config.documenation.gcpProjectId) { error(message: "documentation/gcpProjectId must be set.") }
  }

  // prepare Helm release name here
  config.helmReleaseName = "${config.projectFriendlyName}-" +
    "${config.appName}-" +
    "${config.envDetails.friendlyEnvName}"
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

def getNodeKubeConfigPath(Map cfg, Map envDetails) {
  if(envDetails.kubeConfigPath) {
    // use explicitly specified kube config path if specified
    return envDetails.kubeConfigPath
  } else {
    return "${cfg.kubeConfigPathPrefix}/config-${envDetails.gcpProjectId}-${envDetails.gkeClusterName}"
  }
}

def getNodeDockerTag(Map cfg, Map envDetails, branch, buildNumber) {
  tag = "${cfg.gcpDockerRegistryPrefix}/" +
    "${envDetails.gcpProjectId}/" +
    "${cfg.projectFriendlyName}/" +
    "${cfg.appName}:${branch}.${buildNumber}"
  tag = tag.toLowerCase()
  echo "Docker image tag: ${tag}"
  return tag.replace("/", "-")
}

def getNodeHelmValues(Map envDetails) {
  if(envDetails.helmValues) {
    return envDetails.helmValues
  } else {
    return [:]
  }
}

def getNodeHelmChart(Map envDetails) {
  if(envDetails.helmChart) {
    return envDetails.helmChart
  } else {
    error(message: "No helmChart provided.")
  }
}

def validateSecretsInjection(Map secretsInjection) {
  if(!secretsInjection.jenkinsCredentialsId) { error(message: "secretsInjection/jenkinsCredentialsId must be set") }
  if(!secretsInjection.vaultUrl) { error(message: "secretsInjection/vaultUrl must be set") }
  if(!secretsInjection.secrets) { error(message: "secretsInjection/secrets must be set") }
  for(s in secretsInjection.secrets) {
    if(!s.vaultSecretPath) { error(message: "secretsInjection/secrets[]/vaultSecretPath must be set") }
    if(!s.keyMap) { error(message: "secretsInjection/secrets[]/keyMap[] must be set") }
    for(k in s.keyMap) {
      if(!k.vault) { error(message: "secretsInjection/secrets[]/keyMap[]/vault must be set") }
      if(!k.local) { error(message: "secretsInjection/secrets[]/keyMap[]/local must be set") }
    }
  }
}

def validateEnvDetailsString(String input, Map config) {
  if(!config.envDetails."${input}" || config.envDetails."${input}" == "") {
    error(message: "${input} has to be always set!")
  }
}
