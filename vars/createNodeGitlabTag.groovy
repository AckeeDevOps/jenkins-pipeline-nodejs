enum PatchLevel {
    MAJOR, MINOR, PATCH
}

class SemVer implements Serializable {

    private int major, minor, patch

    SemVer(String version) {
        def versionParts = version.tokenize('.')
        println versionParts
        if (versionParts.size != 3) {
            throw new IllegalArgumentException("Wrong version format - expected MAJOR.MINOR.PATCH - got ${version}")
        }
        this.major = versionParts[0].toInteger()
        this.minor = versionParts[1].toInteger()
        this.patch = versionParts[2].toInteger()
    }

    SemVer(int major, int minor, int patch) {
        this.major = major
        this.minor = minor
        this.patch = patch
    }

    SemVer bump(PatchLevel patchLevel) {
        switch (patchLevel) {
            case PatchLevel.MAJOR:
                return new SemVer(major + 1, 0, 0)
                break
            case PatchLevel.MINOR:
                return new SemVer(major, minor + 1, 0)
                break
            case PatchLevel.PATCH:
                return new SemVer(major, minor, patch + 1)
                break
        }
        return new SemVer()
    }

    String toString() {
        return "${major}.${minor}.${patch}"
    }

}

def call(Map config) {

  dir('repo') {
    sshagent(config.gitlabTagCredentials) {
      // delete local tags, fetch remote
      sh('git tag -l | xargs git tag -d && git fetch -t')

      // list tags
      sh('git tag')

      def branch=config.branch
      def branchShort=branch.take(3)
      def startedBy=config.startedBy

      def result = sh(script:"git tag --sort v:refname | grep '^${branchShort}-[0-9]\\+\\.[0-9]\\+\\.[0-9]\\+' | tail -1 | cut -d- -f2", returnStdout: true).trim()
      def latestVersion = result ? new SemVer("${result}") : new SemVer("0.0.0")
      latestVersion = latestVersion.bump(PatchLevel.PATCH).toString()
      def newTag = "${branchShort}-${latestVersion}"

      sh("""
         prettyDate=\$(date +'%H:%M %Y-%m-%d')
         git tag -a "$newTag" -m "version deployed by $startedBy from branch='$branch' @ \$prettyDate"
         git push origin "$newTag"
         """)
    }
  }
}
