def call() {
  step([
    $class: 'JUnitResultArchiver',
    allowEmptyResults: true,
    healthScaleFactor: 10.0,
    keepLongStdio: true,
    testResults: 'ci-outputs/mocha/test.xml'
  ])
  echo "junit finished. currentBuild.result=${currentBuild.result}"
}
