def call() {
  dir('repo') {
    wrap([$class: 'BuildUser']) {
      try {
        def userId = "${BUILD_USER_ID}"
        userId = sh(script: "echo $userId | cut -d@ -f1", returnStdout: true).trim()
        return userId + ' (manually)'
      } catch(Exception ex) {
        def commiter = sh(script: 'git show -s --pretty=%ae | cut -d@ -f1',  returnStdout: true).trim()
        return commiter + ' (commit author)'
      }
    }
  }
}
