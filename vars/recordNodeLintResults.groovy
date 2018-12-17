def call() {
  step([
    $class: 'CheckStylePublisher',
    pattern: 'ci-outputs/lint/checkstyle-result.xml',
    usePreviousBuildAsReference: false,
    unstableTotalHigh: '0'
  ])
}
