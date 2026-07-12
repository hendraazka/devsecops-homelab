pipeline {
    agent any

    environment {
        IMAGE_NAME = "account-service-jenkins"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                dir('account-service') {
                    sh 'mvn -B clean compile'
                    sh 'mvn -B test'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir('account-service') {
                    sh "docker build -t ${IMAGE_NAME}:${BUILD_NUMBER} ."
                    sh "kind load docker-image ${IMAGE_NAME}:${BUILD_NUMBER} --name devsecops-homelab"
                }
            }
        }

     stage('Deploy to Kubernetes') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-devsecops-homelab', variable: 'KUBECONFIG_FILE')]) {
                    sh '''
                        kubectl --kubeconfig=$KUBECONFIG_FILE set image deployment/account-service account-service=${IMAGE_NAME}:${BUILD_NUMBER}
                        kubectl --kubeconfig=$KUBECONFIG_FILE rollout status deployment/account-service
                    '''
                }
            }
        }

    }

    post {
        success {
            echo 'Pipeline Jenkins berhasil - deploy otomatis ke cluster selesai!'
        }
        failure {
            echo 'Pipeline Jenkins gagal - cek log di atas untuk detail.'
        }
    }
}
