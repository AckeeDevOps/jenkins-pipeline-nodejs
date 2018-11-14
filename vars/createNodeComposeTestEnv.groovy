import groovy.json.*

def call(Map config, String filename) {

  // check Env defined by user in Jenkinsfile
  def initialTestEnv
  if(config.testConfig.nodeTestEnv) {
    if(config.testConfig.nodeTestEnv instanceof Map) {
      initialTestEnv = config.testConfig.nodeTestEnv
    } else {
      error(message: "nodeTestEnv should be Map!")
    }
  } else {
    initialTestEnv = [:]
  }

  template = [
    version: '3.1',
    services: [
      main: [
        image: config.dockerImageTag,
        build: [
          context: './repo'
        ],
        environment: initialTestEnv, // map ENV_VARIABLE:value
        secrets: [], // just array with secret names
        volumes: [
          "./ci-outputs/coverage:/usr/src/app/coverage",
          "./ci-outputs/mocha:/usr/src/app/output"
        ]
      ]
    ],
    secrets: [:]
  ]

  // remove old builds
  sh(script: 'rm -rf ./ci-outputs')
  sh(script: 'mkdir -p ./ci-outputs/coverage')
  sh(script: 'mkdir -p ./ci-outputs/mocha')

  if(config.testConfig.secretsInjection) {
    // prepare data structure for obtainNodeVaultSecrets function
    def secrets = []
    for(c in config.testConfig.secretsInjection.secrets){
      secrets.push([path: c.vaultSecretPath, keyMap: c.keyMap])
    }

    // obtain data from vault
    def secretData = obtainNodeVaultSecrets(
      config.testConfig.secretsInjection.vaultUrl,
      secrets,
      config.testConfig.secretsInjection.jenkinsCredentialsId
    )

    sh(script: "mkdir -p ./secrets") // create directory for docker-compose secrets

    for(c in config.testConfig.secretsInjection.secrets){
      for(k in c.keyMap) {
        if(secretData."${k.local}"){
          secretKeyDataPlain = secretData."${k.local}"
          def form = k.form ? k.form : 'envVar' // set default behaviour of secret
          switch(form) {
            case "envVar":
              // populate template with ENV vars
              template.services.main.environment.put(k.local, secretKeyDataPlain)
              break
            case "file":
              // write the file first
              writeFile(file: "./secrets/${k.local}", text: secretKeyDataPlain)
              // create refferences in the template
              template.secrets.put(k.local, [file: "./secrets/${k.local}"])
              template.services.main.secrets.push(k.local)
              break
            default:
              error("${form} is not supported form of secret.")
              break
          }
        } else {
          error("${k.vault} @ ${c.vaultSecretPath} seems to be non-existent!")
        }
      }
    }
  } else {
    echo("No secrets for the ci-test")
  }

  def manifest = JsonOutput.toJson(template)
  writeFile(file: filename, text: manifest)
}
