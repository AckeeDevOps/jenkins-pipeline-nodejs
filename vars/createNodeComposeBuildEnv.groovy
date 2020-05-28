import groovy.json.*

def call(Map config, String filename) {
  withCredentials([string(credentialsId: config.sshCredentialsId, variable: 'KEY')]) {
    template = [
      version: '3.1',
      services: [
        main: [
          image: "${config.dockerImageName}:${config.dockerImageTag}",
          build: [
            context: './repo',
            args: [
              PRIVATE_KEY: env.KEY,
              CI_BUILD_BRANCH: config.branch,
            ]
          ],
          environment: [NODE_PATH: '.'],
          extra_hosts: [
            "kancl.ack.ee": 192.168.130.252
          ]
        ]
      ]
    ]
    def manifest = JsonOutput.toJson(template)
    writeFile(file: filename, text: manifest)
  }
}
