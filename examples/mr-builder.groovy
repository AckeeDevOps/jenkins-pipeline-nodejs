@Library('jenkins-pipeline-nodejs@master') _

env.SLACK_CHANNEL = "ci-merge-requests"
node() {

  def config = [:]
  config.sshCredentialsId = "jenkins-ssh-key"
  config.dockerImageTag = "test-nodejs"
  config.gitlabCredentialsId = "jenkins-gitlab-credentials"

  config.testConfig = [:]
  config.testConfig.nodeTestEnv = [NODE_ENV: 'test', NODE_PATH: '.']

  try {
    gitlabBuilds(builds: ["build docker image", "run tests", "run lint"]) {

      // start of checkout stage
      stage('Checkout') {
        if (!fileExists('repo')){ sh(script: "rm -rf repo && mkdir -p repo") }
        dir('repo') {
          withCredentials([string(credentialsId: config.gitlabCredentialsId, variable: 'credentials')]) {
            checkout(
              changelog: true,
              poll: true,
              scm: [
                $class: 'GitSCM',
                branches: [[name: "origin/${env.gitlabSourceBranch}"]],
                doGenerateSubmoduleConfigurations: false,
                extensions: [
                  [$class: 'CleanBeforeCheckout'],
                  [
                    $class: 'PreBuildMerge',
                    options: [
                      fastForwardMode: 'FF',
                      mergeRemote: 'origin',
                      mergeStrategy: 'DEFAULT',
                      mergeTarget: "${env.gitlabTargetBranch}"]
                    ]
                ],
                submoduleCfg: [],
                userRemoteConfigs: [
                  [
                    name: 'origin',
                    credentialsId: credentials,
                    url: env.gitlabTargetRepoSshUrl
                  ]
                ]
              ]
            )
          }
        }
      }
      // end of checkout stage

      // start of config stage
      stage('read test config') {
        if (fileExists('./repo/test.cfg.groovy')) {
          // overwrite default test config
          config.testConfig = load('./repo/test.cfg.groovy')

          // overwrite default values if specified
          if(config.testConfig.sshCredentialsId) { config.sshCredentialsId = config.testConfig.sshCredentialsId }
          if(config.testConfig.slackChannel) { config.slackChannel =  config.testConfig.slackChannel}
        } else {
          echo("Test configuration file does not exist.")
        }
      }
      // end of config stage

      // start of build stage
      gitlabCommitStatus(name: "build docker image") {
        stage('Build Docker image') {
          reason='docker image build'
          createNodeComposeBuildEnv(config, './build.json') // create docker-compose file
          sh(script: "docker-compose -f ./build.json build")
        }
      }
      // end of build stage

      gitlabCommitStatus(name: "run tests") {
        stage('Run Docker tests') {
          reason='tests'
          createNodeComposeTestEnv(config, './test.json') // create docker-compose file
          sh(script: "docker-compose -f test.json up --no-start")
          sh(script: "docker-compose -f test.json run main npm run ci-test")

          step([
            $class: 'JUnitResultArchiver',
            allowEmptyResults: true,
            healthScaleFactor: 100,
            keepLongStdio: true,
            testResults: 'ci-outputs/mocha/test.xml'
          ])

          step([
            $class: 'CloverPublisher',
            cloverReportDir: './ci-outputs/coverage',
            cloverReportFileName: 'clover.xml'
          ])

          if (currentBuild.result == 'UNSTABLE') {
            error "Tests results are UNSTABLE"
          }
        }
      }

      gitlabCommitStatus(name: "run lint") {
        stage('Run Docker lint') {
          reason='lint'
          sh(script: "docker-compose -f test.json run main npm run ci-lint")

          step([
            $class: 'CheckStylePublisher',
            failedTotalAll: '0',
            canComputeNew: false,
            pattern: 'ci-outputs/mocha/checkstyle-result.xml'
          ])

          if (currentBuild.result != 'SUCCESS') {
            error "Lint results are UNSTABLE"
          }
        }
      }
    }
    currentBuild.result = 'SUCCESS'

  } catch (e) {
    currentBuild.result = "FAILURE"
    throw e
  } finally {
    notifyNodeBuild(currentBuild.result, "merge request")
    sh(script: "docker-compose -f test.json rm -s -f")
  }
}
