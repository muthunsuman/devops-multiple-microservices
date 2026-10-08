pipeline {
    agent any

    triggers {
        githubPush()
    }

    stages {
        stage('Webhook Test') {
            steps {
                echo 'GitHub webhooks triggered Jenkins!'
            }
        }
    }
}