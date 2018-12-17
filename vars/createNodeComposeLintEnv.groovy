import groovy.json.*

def call(Map config, String filename) {

  template = [
    version: '3.1',
    services: [
      main: [
        image: config.dockerImageTag,
        build: [
          context: './repo'
        ],
        environment: [:], // map ENV_VARIABLE:value
        volumes: [
          "./ci-outputs/lint:/usr/src/app/output"
        ]
      ]
    ],
    secrets: [:]
  ]

  // remove old builds
  sh(script: 'rm -rf ./ci-outputs/lint')

  // create dir structure
  sh(script: 'mkdir -p ./ci-outputs')
  sh(script: 'mkdir -p ./ci-outputs/lint')

  def manifest = JsonOutput.toJson(template)
  writeFile(file: filename, text: manifest)
}
