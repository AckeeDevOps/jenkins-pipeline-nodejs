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
        volumes: [
          "./docs-output:/usr/src/app/docs-output"
        ]
      ]
    ]
  ]

  sh(script: 'rm -rf ./docs-output')
  sh(script: 'mkdir -p ./docs-output')

  def manifest = JsonOutput.toJson(template)
  writeFile(file: filename, text: manifest)
}
