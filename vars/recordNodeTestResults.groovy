def call(boolean allowEmptyResults, float healthScaleFactor) {
  step([
    $class: 'JUnitResultArchiver',
    allowEmptyResults: allowEmptyResults,
    healthScaleFactor: healthScaleFactor,
    keepLongStdio: true,
    testResults: 'ci-outputs/junit/test.xml'
  ])
  echo "junit finished. currentBuild.result=${currentBuild.result}"
}
