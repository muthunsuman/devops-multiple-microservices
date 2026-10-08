
pipeline {
    agent any

    triggers {
        githubPush()
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 10, unit: 'MINUTES')
    }

    environment {
        GIT_REPO = 'https://github.com/muthunsuman/devops-multiple-microservices.git'
        GIT_BRANCH = 'main'
    }

    stages {
        stage('Checkout Source Code') {
            steps {
                deleteDir()

                git branch: "${GIT_BRANCH}",
                    url: "${GIT_REPO}"

                echo 'GitHub repository checked out successfully.'
            }
        }

        stage('Webhook Test') {
            steps {
                echo 'Jenkins pipeline started successfully!'

                sh '''
                    echo "Current Git commit:"
                    git rev-parse --short HEAD

                    echo "Latest commit details:"
                    git log -1 --oneline

                    echo "Repository root:"
                    git rev-parse --show-toplevel
                '''
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Checkout and webhook test completed.'
        }

        failure {
            echo 'FAILED: Check the stage logs for the exact error.'
        }
    }
}