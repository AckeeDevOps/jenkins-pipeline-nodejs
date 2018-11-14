import groovy.json.*

def call(Map config) {
  // prepare data structure for obtainNodeVaultSecrets function
  def secrets = []
  for(c in config.secretsInjection.secrets){
    secrets.push([path: c.vaultSecretPath, keyMap: c.keyMap])
  }

  // obtain data from vault
  def secretData = obtainNodeVaultSecrets(
    config.secretsInjection.vaultUrl,
    secrets,
    config.secretsInjection.jenkinsCredentialsId
  )

  def outputData = [:]

  // prepare structure here
  for(c in config.secretsInjection.secrets){
    for(k in c.keyMap) {
      // select data from the obtained Map according to configuration
      if(secretData."${k.local}"){
        kuberneteDataPlain = secretData."${k.local}"

        // convert data to the base64 format
        kubernetesData = kuberneteDataPlain.toString().bytes.encodeBase64().toString()

        // push a new key to the kubernetes manifest template
        outputData.put(k.local, kubernetesData)
      } else {
        error "${k.vault} @ ${c.vaultSecretPath} seems to be non-existent!"
      }
    }
  }

  // serialize template to json so we can push it to Kubernetes
  return outputData
}
