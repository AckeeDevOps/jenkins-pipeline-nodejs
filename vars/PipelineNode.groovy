def call(body) {

  def cfg = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = cfg
  body()

  def agent = (cfg.agent != null) ? cfg.agent : ''

  node(agent) {
    // set current step for the notification handler
    def pipelineStep = "start"
    def repositoryUrl = scm.getUserRemoteConfigs()[0].getUrl()
    def config = processNodeConfig(cfg, env.BRANCH_NAME, env.BUILD_NUMBER, repositoryUrl)

    try {
      // https://jenkins.io/doc/pipeline/steps/workflow-scm-step/
      stage('Checkout') {
        pipelineStep = "checkout"
        if (!fileExists('repo')){ new File('repo').mkdir() }
        dir('repo') { checkout scm }

        // create a changelog
        def changelog = getChangelog()
        echo(changelog)
        writeFile(file: "./changelog.txt", text: changelog)
      }

      // start of Build stage
      stage('Build') {
        pipelineStep = "build"
        createNodeComposeBuildEnv(config, './build.json') // create docker-compose file
        sh(script: "docker-compose -f ./build.json build")
      }
      // end of Build stage

      // start of Docker push image stage
      stage('Push image') {
        pipelineStep = "push image"
        sh(script: "gcloud auth configure-docker --configuration ${config.envDetails.gcpProjectId}")
        sh(script: "docker push ${config.dockerImageTag}")
      }
      // end of Docker push image stage

      // start of Test stage
      stage('Test') {
        pipelineStep = "test"
        if (config.testConfig) {
          createNodeComposeTestEnv(config, './test.json') // create docker-compose file
          sh(script: "docker-compose -f test.json up --no-start")
          sh(script: "docker-compose -f test.json run main npm run ci-test")
        } else {
          echo "Tests have been skipped based on the Jenkinsfile configuration"
        }
      }
      // end of Test stage

      // start of Lint stage
      stage('Lint') {
        pipelineStep = "lint"
        if(config.runLint) {
          createNodeComposeLintEnv(config, './lint.json') // create docker-compose file
          sh(script: "docker-compose -f lint.json up --no-start")
          sh(script: "docker-compose -f lint.json run main npm run ci-lint")

          // set correct path to tested files in the lint results
          sh(script: "sed -i 's#/usr/src/app/#${config.workspace}/repo/#g' ci-outputs/lint/checkstyle-result.xml")

          step([
            $class: 'CheckStylePublisher',
            pattern: 'ci-outputs/lint/checkstyle-result.xml',
            usePreviousBuildAsReference: false,
            unstableTotalHigh: '0'
          ])
        }
      }
      // end of Lint stage

      // start of Documentation stage
      stage('Documentation') {
        if(config.documentation) {
          createNodeComposeDocsEnv(config, './documentation.json')
          sh(script: "docker-compose -f documentation.json up --no-start")
          sh(script: "docker-compose -f documentation.json run main npm run docs")
          createNodeDocumentationGcsBucket(config)
          uploadNodeDocumentation(config)
        } else {
          echo("skipping documentation.")
        }
      }
      // end of Documentation stage

      // start of Deploy stage
      stage('Deploy') {
        pipelineStep = "deploy"

        // if specified, obtain secrets
        def secretData
        if(config.secretsInjection) {
          // get secrets from Vault
          secretData = createNodeSecretsManifest(config)
        } else {
          echo "Skipping injection of credentials"
        }

        // create helm values file
        def helmValuesJson = createNodeHelmValues(config, secretData)
        writeFile(file: "./values.json", text: helmValuesJson)

        // try to create yaml file from template first
        // this checks whether values.yaml contains required fields
        def tmplOut = config.debugMode ? "./tmpl.out.yaml"  : "/dev/null"
        sh(script: "helm template -f ./values.json ${config.helmChart} -n ${config.helmReleaseName} > ${tmplOut}")

        // upgrade or install release
        def deployCommand = "helm upgrade " +
          "--install " +
          "--kubeconfig ${config.kubeConfigPath} " +
          "-f ./values.json " +
          "--namespace ${config.envDetails.k8sNamespace} " +
          "${config.helmReleaseName} " +
          "${config.helmChart} "

        sh(script: deployCommand + " --dry-run")
        if(!config.dryRun) { sh(script: deployCommand) }

        // get status of the services within the namespace
        if(!config.dryRun) {
        sh(script: "kubectl get svc -n ${config.envDetails.k8sNamespace} -o json | " +
          "jq '.items[] | {name: .metadata.name, ports: .spec.ports[]}'")
        }
      }
      // end of Deploy stage

    } catch(err) {
      currentBuild.result = "FAILURE"
      println(err.toString());
      println(err.getMessage());
      println(err.getStackTrace());
      throw err
    } finally {
      // remove build containers
      if(fileExists('build.json')) {
        sh(script: 'docker-compose -f build.json rm -s -f')
      }

      // remove documentation containers
      if(config.documentation) {
        if(fileExists('documentation.json')) {
          sh(script: 'docker-compose -f documentation.json rm -s -f')
        }
      }

      // remove test containers
      if(config.testConfig) {
        if(fileExists('test.json')) {
          sh(script: 'docker-compose -f test.json rm -s -f')
        }
      }

      // remove lint containers
      if(config.runLint) {
        if(fileExists('lint.json')) {
          sh(script: 'docker-compose -f lint.json rm -s -f')
        }
      }

      // sometimes you need to check these files you know
      if(!config.debugMode) {
        sh(script: 'rm -rf ./test.json')
        sh(script: 'rm -rf ./build.json')
        sh(script: 'rm -rf ./secrets')
        sh(script: 'rm -rf ./values.json')
      }

      if(config.testConfig) {
        // publish test results
        step([
          $class: 'JUnitResultArchiver',
          allowEmptyResults: true,
          healthScaleFactor: 10.0,
          keepLongStdio: true,
          testResults: 'ci-outputs/mocha/test.xml'
        ])
        echo "junit finished. currentBuild.result=${currentBuild.result}"

        // publish coverage results
        step([
          $class: 'CloverPublisher',
          cloverReportDir: './ci-outputs/coverage',
          cloverReportFileName: 'clover.xml',
          failingTarget: [
            conditionalCoverage: 0,
            methodCoverage: 0,
            statementCoverage: 0
          ],
          healthyTarget: [
            conditionalCoverage: 80,
            methodCoverage: 70,
            statementCoverage: 80
          ],
          unhealthyTarget: [
            conditionalCoverage: 0,
            methodCoverage: 0,
            statementCoverage: 0
          ]
        ])
        echo "CloverPublisher finished. currentBuild.result=${currentBuild.result}"
      }

      // send slack notification
      if(config.slackChannel) {
        notifyNodeBuild(
          buildStatus: currentBuild.result,
          buildType: 'Build',
          channel: config.slackChannel,
          reason: pipelineStep
        )
      }
    }
  }
}
