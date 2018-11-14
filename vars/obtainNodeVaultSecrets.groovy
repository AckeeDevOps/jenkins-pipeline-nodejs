import groovy.json.*

def call(String vaultUrl, List secrets, String jenkinsSecretName){
  withCredentials([string(credentialsId: jenkinsSecretName, variable: 'VAULT_TOKEN')]) {
    env.VAULT_ADDR = vaultUrl
    // prepare empty map for Vault data
    def secretData = [:]
    // got through specification and GET Vault secrets
    for(s in secrets){
      echo "getting credentials @ ${s.path}"
      // read secrets with vault client
      def cmdOutput = sh(script: "vault read -field=data -format=json ${s.path}", returnStdout: true)
      // parse vault output
      def d = new JsonSlurperClassic().parseText(cmdOutput)
      for(k in s.keyMap) {
        secretData.put(k.local, d."${k.vault}")
      }
    }

    // integrity check
    errors = []
    for(s in secrets){
      for(k in s.keyMap) { if( ! secretData."${k.local}"){ errors.push(s) } }
    }

    if(errors.size() > 0){ error "Some secrets were not obtained: ${errors.dump()}" }
    return secretData
  }
}
