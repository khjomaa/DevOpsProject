def label = "helm-jenkins-${UUID.randomUUID().toString()}"
def gitRepoUrl = "https://github.com/khjomaa/DevOpsProject.git"
//def gitHubCredentials = "GitHubCreds"

podTemplate(label: label,
        containers: [
                containerTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:alpine'),
                containerTemplate(name: 'helm', image: 'dtzar/helm-kubectl', command: 'cat', ttyEnabled: true)
        ],
        volumes: [
                hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock'),
        ],
) {
    node(label) {
        stage('Checkout Repo') {
            git credentialsId: gitHubCredentials, url: gitRepoUrl
            git gitRepoUrl
        }

        def props = readProperties file:'./deployment/jenkins/pipelines.properties'
        def baseDeployDir = props["baseDeployDir"]
        def appNamespace = props["appNamespace"]
        def releaseName = props["releaseName"]
        def helmRepository = props["helmRepository"]

        stage('Add Stable Repo') {
            container('helm') {
                sh "helm repo add stable https://kubernetes-charts.storage.googleapis.com"
                sh "helm repo update"
            }
        }

        stage('Deploy ChartMuseum and Add Repo') {
            container('helm') {
                sh "helm upgrade --install --wait chartmuseum stable/chartmuseum -f  ${baseDeployDir}/helm/chartmuseum/my-values.yaml -n charts"
                sh "helm plugin install https://github.com/chartmuseum/helm-push"
                echo "Adding ChartMuseum repo"
                NODE_PORT = sh (
                        script: 'kubectl get --namespace charts -o jsonpath="{.spec.ports[0].nodePort}" services chartmuseum-chartmuseum',
                        returnStdout: true
                ).trim()

                NODE_IP = sh (
                        script: 'kubectl get nodes --namespace charts -o jsonpath="{.items[0].status.addresses[0].address}"',
                        returnStdout: true
                ).trim()
                sh "helm repo add ${helmRepository} http://${NODE_IP}:${NODE_PORT}"
                sh "helm repo update"
                sh "helm repo list"
            }
        }

        stage('Package Charts') {
            container('helm') {
                echo "Packaging consumer chart"
                sh "helm package ${baseDeployDir}/helm/consumer"

                echo "Packaging producer chart"
                sh "helm package ${baseDeployDir}/helm/producer"
            }
        }

        stage('Push charts to ChartMuseum') {
            container('helm') {
                sh "helm push -f ${baseDeployDir}/helm/consumer/ ${helmRepository}"
                sh "helm push -f ${baseDeployDir}/helm/producer/ ${helmRepository}"
                sh "helm repo update"
            }
        }

        stage('Deploy Application') {
            container('helm') {
                sh "helm dep update ${baseDeployDir}/helm/app"
                sh "helm upgrade --install --wait ${releaseName} ${baseDeployDir}/helm/app -n ${appNamespace}"
            }
        }
    }
}