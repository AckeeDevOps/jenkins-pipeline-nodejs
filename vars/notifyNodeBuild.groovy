env.CHANGELOG_PATH = "changelog.txt"
env.SLACK_CHANNEL = "ci-merge-requests"

def call( Map args ) {
  def changelogPath = "changelog.txt"
  def channel = args.channel ?: "ci-merge-requests"
  def buildType = args.buildType ?: 'Build'

  // build status of null means successful
  String buildStatus = args.buildStatus ?: currentBuild.result ?: 'SUCCESS'
  String reason = args.reason ?: 'reason unknown'

  // Default values
  def author
  if(args.author) { author = args.author } else { author = getAuthorName() }
  def workspace = pwd()
  changelogPath = "${workspace}/${changelogPath}"
  def changelog

  if (fileExists(changelogPath)) {
    changelog = readFile(changelogPath)
  } else {
    changelog = ""
  }

  def color = args.color ?: 'warning'
  def summary = args.summary ?: ''
  def jobName = env.gitlabSourceRepoName ? "${env.gitlabSourceRepoName}/${env.gitlabSourceBranch}" : env.JOB_NAME

  if(summary.equalsIgnoreCase('')) {

    String header = "$buildType #$env.BUILD_NUMBER $jobName status *${buildStatus.toLowerCase()}* "
    String footer = " (<$env.BUILD_URL|open>)\n" + changelog

    // Override default values based on build status
    if (buildStatus == 'SUCCESS') {
      color = "good"
      summary = header + "started by $author" + footer
    }
    else if (buildStatus == 'UNSTABLE') {
      color = "warning"
      summary = header + "started by @$author" + footer
    }
    else if (buildStatus == "FAILURE") {
      summary = header + "(reason: $reason) started by @$author" + footer
      color = "danger"
    }
  }
  // Send notifications
  slackSend message: summary, channel: channel, color: color
}

def call(String buildStatus, String reason) {
  // build status of null means successful
  buildStatus =  buildStatus ?: 'SUCCESS'
  if(env.BUILD_TYPE==null) env.BUILD_TYPE='Build'
  notifyNodeBuild(buildStatus: buildStatus, buildType: env.BUILD_TYPE, channel: env.SLACK_CHANNEL, reason: reason )
}

def call(String buildName, String buildStatus, String reason) {
  env.BUILD_TYPE=buildName
  notifyNodeBuild(buildStatus, reason)
}
