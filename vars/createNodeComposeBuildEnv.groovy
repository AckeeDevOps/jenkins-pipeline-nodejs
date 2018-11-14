import groovy.json.*

def call(Map config, String filename) {
  withCredentials([string(credentialsId: config.sshCredentialsId, variable: 'KEY')]) {
    template = [
      version: '3.1',
      services: [
        main: [
          image: config.dockerImageTag,
          build: [
            context: './repo',
            args: [
              PRIVATE_KEY: env.KEY
            ]
          ],
          environment: [NODE_PATH: '.']
        ]
      ]
    ]
    def manifest = JsonOutput.toJson(template)
    writeFile(file: filename, text: manifest)
  }
}
