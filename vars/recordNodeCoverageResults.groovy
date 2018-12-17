def call() {
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
