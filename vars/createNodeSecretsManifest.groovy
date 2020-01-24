def call(Map config) {

  if(config.envDetails.injectSecretsDeployment) {
    // set vaultier PARAMS first
    withCredentials([string(credentialsId: config.envDetails.vaultTokenSecretId, variable: 'VAULT_TOKEN')]) {
      env.VAULTIER_VAULT_ADDR = config.envDetails.vaultAddr
      env.VAULTIER_VAULT_TOKEN = env.VAULT_TOKEN
      env.VAULTIER_ENVIRONMENT = config.envDetails.friendlyEnvName
      env.VAULTIER_OUTPUT_FORMAT = "helm"
      env.VAULTIER_SECRET_SPECS_PATH = config.envDetails.secretSpecsPath ?: "${config.workspace}/repo/secrets.yaml"
      env.VAULTIER_SECRET_OUTPUT_PATH = "${config.workspace}/secrets-deployment.json"

      // obtain secrets from Vault
      sh(script: "vaultier2")
    }
  } else {
    echo("No secrets for the deployment")
    
    // create empty map of secrets in secrets file so pipeline won't fail
    sh(script: "echo '{\"secrets\": {}}' > secrets-deployment.json")
  }
}
