import groovy.json.*

def call(Map config, String filename) {

  // check Env defined by user in Jenkinsfile
  def initialTestEnv
  if(config.envDetails.nodeTestEnv) {
    if(config.envDetails.nodeTestEnv instanceof Map) {
      initialTestEnv = config.envDetails.nodeTestEnv
    } else {
      error(message: "nodeTestEnv should be Map!")
    }
  } else {
    // create empty Map if not specified
    initialTestEnv = [:]
  }

  template = [
    version: '3.1',
    services: [
      main: [
        image: "${config.dockerImageName}:${config.dockerImageTag}",
        build: [
          context: './repo'
        ],
        environment: initialTestEnv, // map ENV_VARIABLE:value
        volumes: [
          "./ci-outputs/coverage:/usr/src/app/coverage",
          "./ci-outputs/junit:/usr/src/app/output",
          "./ci-outputs/npm-logs:/root/.npm/_logs/"
        ]
      ]
    ]
  ]

  // remove old builds
  sh(script: 'rm -rf ./ci-outputs/coverage')
  sh(script: 'rm -rf ./ci-outputs/junit')
  sh(script: 'rm -rf ./ci-outputs/npm-logs')

  // create dir structure
  sh(script: 'mkdir -p ./ci-outputs')
  sh(script: 'mkdir -p ./ci-outputs/coverage')
  sh(script: 'mkdir -p ./ci-outputs/junit')
  sh(script: 'mkdir -p ./ci-outputs/npm-logs')

  if(config.envDetails.injectSecretsTest) {
    // set vaultier PARAMS first
    withCredentials([string(credentialsId: config.envDetails.vaultTokenSecretId, variable: 'VAULT_TOKEN')]) {
      // see https://github.com/AckeeDevOps/vaultier
      env.VAULTIER_VAULT_ADDR = config.envDetails.vaultAddr
      env.VAULTIER_VAULT_TOKEN = env.VAULT_TOKEN
      env.VAULTIER_BRANCH = config.branch
      env.VAULTIER_RUN_CAUSE = "test"
      env.VAULTIER_OUTPUT_FORMAT = "dotenv"
      env.VAULTIER_SECRET_SPECS_PATH = config.envDetails.secretSpecsPath ?: "${config.workspace}/repo/secrets.yaml"
      env.VAULTIER_SECRET_OUTPUT_PATH = "${config.workspace}/secrets-test.json"
      
      // see https://github.com/AckeeDevOps/envdocksec
      env.ENVDOCKSEC_INPUT_FILE = "${config.workspace}/secrets-test.json"
      env.ENVDOCKSEC_OUTPUT_DIRECTORY = "${config.workspace}/secrets"
      env.ENVDOCKSEC_OUTPUT_MANIFEST = "${config.workspace}/test-volumes.json"
      env.ENVDOCKSEC_DOCKER_TARGET_DIRECTORY = "/etc/secrets"
      env.ENVDOCKSEC_CREATE_OUTPUT_DIRECTORY = "true"

      // obtain secrets from Vault
      sh(script: "vaultier") // see https://github.com/AckeeDevOps/vaultier
      sh(script: "envdocksec") // see https://github.com/AckeeDevOps/envdocksec

      // add volume with JSON file to compose manifest
      template.services.main.volumes.add("${config.workspace}/secrets-test.json:/etc/secrets/cfg.json")
      
      // specify path to JSON file
      template.services.main.environment.CFG_JSON_PATH = "/etc/secrets/cfg.json"
    }
  } else {
    echo("No secrets for the ci-test")
    sh(script: "echo '{}' > secrets-test.json")
    sh(script: "echo '[]' > test-volumes.json")
  }

  def manifest = JsonOutput.toJson(template)
  writeFile(file: 'test-tmp.json', text: manifest)
  
  // merge secrets and docker-compose
  sh(script: "jq '.services.main.environment += input' test-tmp.json secrets-test.json > test-tmp1.json")
  // append volumes with secrets
  sh(script: "jq '.services.main.volumes += input' test-tmp1.json test-volumes.json > ${filename}")
  
  // remove unneeded files
  if(!config.envDetails.debugMode){ sh(script: "rm -rf test-tmp.json secrets-test.json") }
}
