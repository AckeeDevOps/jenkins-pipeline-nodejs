def call(boolean allowEmptyResults, float healthScaleFactor, String testResults) {
  step([
    $class: 'JUnitResultArchiver',
    allowEmptyResults: allowEmptyResults,
    healthScaleFactor: healthScaleFactor,
    keepLongStdio: true,
    testResults: testResults
  ])
  echo "junit finished. currentBuild.result=${currentBuild.result}"
}
