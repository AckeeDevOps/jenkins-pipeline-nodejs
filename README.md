# Jenkins pipeline library for Nodejs deployment

This is Jenkins pipeline we that are using for the deployment of Nodejs applications to the GKE clusters. It's meant to be simple and straightforward. It also addresses secrets management
with Hashicorp's Vault so you can store your passwords, API keys etc. in the secure store
and leverage functionality of Nodejs libraries such as [dotenv](https://www.npmjs.com/package/dotenv) (or similar) to maintain configuration variables in the different application environments.

## Requirements for your Jenkins workers

**Software**

This pipeline is using variety of third party software. Please make sure your Jenkins worker
has following binaries in the PATH:

- helm (pipeline has been tested with [2.11.0](https://storage.googleapis.com/kubernetes-helm/helm-v2.11.0-linux-amd64.tar.gz))
- docker-compose (pipeline has been tested with [1.23.1](https://github.com/docker/compose/releases/download/1.23.1/docker-compose-Linux-x86_64))
- vault (pipeline has been tested with [0.11.4](https://releases.hashicorp.com/vault/0.11.4/vault_0.11.4_linux_amd64.zip))

**Kubeconfig naming convention**

Each configured environment in the Jenkinsfile can have `kubeConfigPath` option populated in the Jenkinsfile. It's the full path of the kubeconfig for the given cluster. However you can also use the automated kubeconfig name resolution, in this case you need to save kubeconfigs in the following format:

```
config-${project_id}-${cluster_name}
```

Then just set `kubeConfigPathPrefix` and you're good to go.

## Features

### Secrets injection
TBD

### Automatic documentation compilation

If `documentation` variable specified, pipilene will try to generate
documentation with `npm run docs` command. Your nodejs script should
be configured to generate `index.html` file to the `./docs-output`
directory.

**Example:**

```json
...
"scripts": {
  "docs": "aglio -i docs/api.apib --theme-variables flatly -o docs-output/index.html"
},
...
```

**Configuration options**
- `documentation`/`bucketUrl` for example `gs://bucket-name.your.domain`
- `documentation`/`storageClass` for example `regional`
- `documentation`/`location` for example `europe-west3`
- `documentation`/`pathPrefix` for example `project-name`

## Jenkinsfile options

**projectFriendlyName**

This option must be set, typically it's a parent project name for all your
micro services e.g. `order-hammer` so you can `api` micro service call as
`order-hammer-api`

## Jenkinsfile format

**Simple example without secrets injection**

```groovy
PipelineNode{
  projectFriendlyName = "your-awesome-project-name"

  kubeConfigPathPrefix = "/home/jenkins/.kube"
  gcpDockerRegistryPrefix = "eu.gcr.io"
  sshCredentialsId = "jenkins-ssh-key"

  documentation = [:] // this won't trigger documentation stage

  branchEnvs = [
    master: [
      friendlyEnvName: "production",
      gcpProjectId: "gcp-project-id1234",
      gkeClusterName: "production",
      k8sNamespace: "default",
      helmChart: "repo/helm/chart",
      helmValues: [
        deployment: [
          replicaCount: 2,
          hcActive: true,
          hcPath: '/healtz',
          hcPort: 3000,
          requestsCpu: "100m",
          requestsMemory: "100Mi",
          limitsCpu: "500m",
          limitsMemory: "512Mi"
        ]
      ]
    ],
    stage: [
      friendlyEnvName: "stage",
      gcpProjectId: "gcp-project-id1234",
      gkeClusterName: "stage",
      k8sNamespace: "default",
      helmChart: "repo/helm/chart",
      helmValues: [
        deployment: [
          replicaCount: 1,
          hcActive: true,
          hcPath: '/healtz',
          hcPort: 3000,
          requestsCpu: "100m",
          requestsMemory: "100Mi",
          limitsCpu: "200m",
          limitsMemory: "256Mi"
        ]
      ]
    ],
    development: [
      friendlyEnvName: "development",
      gcpProjectId: "gcp-project-id1234",
      gkeClusterName: "development",
      k8sNamespace: "default",
      helmChart: "repo/helm/chart",
      helmValues: [
        deployment: [
          replicaCount: 1,
          hcActive: true,
          hcPath: '/healtz',
          hcPort: 3000,
          requestsCpu: "100m",
          requestsMemory: "100Mi",
          limitsCpu: "200m",
          limitsMemory: "256Mi"
        ]
      ]
    ],
  ]

  testConfig = [
    nodeTestEnv: [NODE_ENV: 'test', NODE_PATH: '.']
  ]
}
```

**Example with secrets injection for the production environment**

```groovy
PipelineNode{
  projectFriendlyName = "your-awesome-project-name"

  kubeConfigPathPrefix = "/home/jenkins/.kube"
  gcpDockerRegistryPrefix = "eu.gcr.io"
  sshCredentialsId = "jenkins-ssh-key"

  branchEnvs = [
    master: [
      friendlyEnvName: "production",
      gcpProjectId: "gcp-project-id1234",
      gkeClusterName: "production",
      k8sNamespace: "default",
      helmChart: "repo/helm/chart",
      helmValues: [
        deployment: [
          replicaCount: 2,
          hcActive: true,
          hcPath: '/healtz',
          hcPort: 3000,
          requestsCpu: "100m",
          requestsMemory: "100Mi",
          limitsCpu: "500m",
          limitsMemory: "512Mi"
        ]
      ],
      secretsInjection: [
        jenkinsCredentialsId: 'jenkins-secret-with-vault-token',
        vaultUrl: 'https://your-vault-url.co.uk',
        secrets: [
          [
            vaultSecretPath: 'secret/data/path/to/your/db/secret',
            keyMap: [
              [vault: 'mysqlUser', local: 'mysqlUser'],
              [vault: 'mysqlPassword', local: 'mysqlPassword']
            ]
          ]
        ]
      ]
    ],
    stage: [
      friendlyEnvName: "stage",
      gcpProjectId: "gcp-project-id1234",
      gkeClusterName: "stage",
      k8sNamespace: "default",
      helmChart: "repo/helm/chart",
      helmValues: [
        deployment: [
          replicaCount: 1,
          hcActive: true,
          hcPath: '/healtz',
          hcPort: 3000,
          requestsCpu: "100m",
          requestsMemory: "100Mi",
          limitsCpu: "200m",
          limitsMemory: "256Mi"
        ]
      ]
    ],
    development: [
      friendlyEnvName: "development",
      gcpProjectId: "gcp-project-id1234",
      gkeClusterName: "development",
      k8sNamespace: "default",
      helmChart: "repo/helm/chart",
      helmValues: [
        deployment: [
          replicaCount: 1,
          hcActive: true,
          hcPath: '/healtz',
          hcPort: 3000,
          requestsCpu: "100m",
          requestsMemory: "100Mi",
          limitsCpu: "200m",
          limitsMemory: "256Mi"
        ]
      ]
    ],
  ]

  testConfig = [
    nodeTestEnv: [NODE_ENV: 'test', NODE_PATH: '.']
  ]
}
```

Keys from the specification will be injected to the Helm values (base64 encoded)
under the `secrets.data` key so you can access them in your Helm chart as usual, for example:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: mysql-credentials
type: Opaque
data:
  mysqlUser: {{ required "required" .Values.secrets.data.mysqlUser }}
  mysqlPassword: {{ required "required" .Values.secrets.data.mysqlPassword }}
```

Then you can refer these secrets in yout Kubernetes manifests, for example:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: secret-envars-test-pod
spec:
  containers:
  - name: envars-test-container
    image: nginx
    env:
    - name: MYSQL_USER
      valueFrom:
        secretKeyRef:
          name: mysql-credentials
          key: mysqlUser
    - name: MYSQL_PASSWORD
      valueFrom:
        secretKeyRef:
          name: mysql-credentials
          key: mysqlPassword
```

## Dockerfile format
As you may have noticed - Jenkinsfile contains `jenkins-ssh-key` option. What is it for?
Well sometimes you need to use private repositories during the `npm install` phase so
this is the way how to do it. You just need to create a new Jenkins secret with the
private RSA key and adjust your Dockerfile to perform multi-stage build. Here's the example:

```Dockerfile
# BUILDER IMAGE
FROM node:8.12.0 AS builder
ARG PRIVATE_KEY
WORKDIR /usr/src/app
COPY . /usr/src/app
RUN mkdir ~/.ssh/
# PRIVATE_KEY=$(sed -E ':a;N;$!ba;s/\r{0,1}\n/\\n/g' my.key)
RUN echo $PRIVATE_KEY > ~/.ssh/id_rsa
RUN chmod 0400 ~/.ssh/id_rsa
RUN eval `ssh-agent -s` && ssh-add ~/.ssh/id_rsa
RUN ssh-keyscan your.private.gitlab.domain.co.uk > /root/.ssh/known_hosts
# build command
RUN npm install

# MAIN IMAGE
FROM node:8.12.0
ENV NODE_PATH=./config:./app
WORKDIR /usr/src/app
COPY --from=builder /usr/src/app /usr/src/app
EXPOSE 3000
CMD [ "npm", "start" ]
```

## Future work
- enhance processing of Jenkinsfile variables. Some sort of class wrapper would be nice

## Credits
- [@bartimar](https://github.com/bartimar) Marek Bartík
- [@DavidKotalik](https://github.com/DavidKotalik) David Kotalík
- [@arteal](https://github.com/arteal) Tomáš Hejátko
- [@vranystepan](https://github.com/vranystepan) Štěpán Vraný
