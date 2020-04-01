def label = "docker-jenkins-${UUID.randomUUID().toString()}"
def gitRepoUrl = "https://github.com/khjomaa/DevOpsProject.git"
def gitHubCredentials = "GitHubCreds"

podTemplate(label: label,
        containers: [
                containerTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:alpine'),
                containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),
        ],
        volumes: [
                hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock'),
        ],
) {
    node(label) {
        stage('Checkout Repo') {
            git credentialsId: gitHubCredentials, url: gitRepoUrl
        }

        def props = readProperties file:'./deployment/jenkins/pipelines.properties'
        def tag = props["version"]
        def consumerDir = props["consumerDir"]
        def producerDir = props["producerDir"]
        def consumerImage = props["consumerImage"]
        def producerImage = props["producerImage"]
        def DockerHubCreds = props["DockerHubCreds"]


        stage('Build and Push Consumer Image') {
            container('docker') {
                docker.withRegistry('', DockerHubCreds) {
                    sh "docker build -f ${consumerDir}/Dockerfile -t ${consumerImage}:${tag} ${consumerDir}"
                    sh "docker push ${consumerImage}:${tag}"
                    sh "docker tag ${consumerImage}:${tag} ${consumerImage}:latest"
                    sh "docker push ${consumerImage}:latest"
                }
            }
        }

        stage('Build and Push Producer Image') {
            container('docker') {
                docker.withRegistry('', DockerHubCreds) {
                    sh "docker build -f ${producerDir}/Dockerfile -t ${producerImage}:${tag} ${producerDir}"
                    sh "docker push ${producerImage}:${tag}"
                    sh "docker tag ${producerImage}:${tag} ${producerImage}:latest"
                    sh "docker push ${producerImage}:latest"
                }
            }
        }
    }
}