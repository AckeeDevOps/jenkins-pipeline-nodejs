import groovy.json.*

def call(Map config, String filename) {

  template = [
    version: '3.1',
    services: [
      main: [
        image: "${config.dockerImageName}:${config.dockerImageTag}",
        build: [
          context: './repo'
        ],
        user: 'root',
        environment: [:], // map ENV_VARIABLE:value
        volumes: [
          "./ci-outputs/npm-audit:/usr/src/app/output"
        ]
      ]
    ],
    secrets: [:]
  ]

  // remove old builds
  sh(script: 'rm -rf ./ci-outputs/npm-audit')

  // create dir structure
  sh(script: 'mkdir -p ./ci-outputs')
  sh(script: 'mkdir -p ./ci-outputs/npm-audit')
  sh(script: 'chmod -R 777 ./ci-outputs')

  def manifest = JsonOutput.toJson(template)
  writeFile(file: filename, text: manifest)
}
