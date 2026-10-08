pipeline {
agent any

```
triggers {
    githubPush()
}

options {
    timestamps()
    disableConcurrentBuilds()
    timeout(time: 60, unit: 'MINUTES')
    skipDefaultCheckout(true)
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

            sh '''
                git fetch origin \
                  +refs/heads/main:refs/remotes/origin/main
            '''

            script {
                env.CURRENT_COMMIT = sh(
                    script: 'git rev-parse HEAD',
                    returnStdout: true
                ).trim()

                // Find the previous commit on main.
                // This works even when the previous build failed.
                def previous = sh(
                    script: 'git rev-parse HEAD^',
                    returnStatus: true
                ) == 0
                    ? sh(
                        script: 'git rev-parse HEAD^',
                        returnStdout: true
                    ).trim()
                    : ''

                if (previous) {
                    env.PREVIOUS_COMMIT = previous
                    env.CHANGE_BASE_FOUND = 'true'
                } else {
                    env.CHANGE_BASE_FOUND = 'false'
                    env.BUILD_SERVICES = ''
                    echo 'Initial commit: no parent commit to compare.'
                }
            }
        }
    }

    stage('Detect Changed Services') {
        steps {
            script {
                def services = [
                    'api-gateway',
                    'auth-service',
                    'cart-service',
                    'inventory-service',
                    'notification-service',
                    'order-service',
                    'payment-service',
                    'product-service',
                    'shipping-service',
                    'user-service'
                ]

                if (env.CHANGE_BASE_FOUND != 'true') {
                    env.BUILD_SERVICES = ''
                    echo 'No parent commit. Skipping service builds safely.'
                } else {
                    def changed = sh(
                        script: "git diff --name-only ${env.PREVIOUS_COMMIT} ${env.CURRENT_COMMIT}",
                        returnStdout: true
                    ).trim()

                    echo "Changed files:\n${changed}"

                    def files = changed
                        ? changed.readLines()
                        : []

                    def selected = services.findAll { service ->
                        files.any { file ->
                            file.startsWith("services/${service}/")
                        }
                    }

                    env.BUILD_SERVICES = selected.join(',')

                    if (selected) {
                        echo "Changed services only: ${selected.join(', ')}"
                    } else {
                        echo 'No service changes detected. Skipping all service builds and tests.'
                    }
                }
            }
        }
    }

    stage('Build Changed Services') {
        when {
            expression {
                return !!env.BUILD_SERVICES?.trim()
            }
        }

        steps {
            script {
                def selected = env.BUILD_SERVICES.split(',')
                def tasks = [:]

                for (serviceName in selected) {
                    def service = serviceName.trim()

                    tasks[service] = {
                        stage("Build: ${service}") {
                            dir("services/${service}") {
                                sh '''
                                    set -eu

                                    if [ -f ./mvnw ]; then
                                        chmod +x ./mvnw
                                        ./mvnw -B -DskipTests package
                                    elif command -v mvn >/dev/null 2>&1; then
                                        mvn -B -DskipTests package
                                    else
                                        echo "ERROR: Maven or Maven wrapper is required."
                                        exit 1
                                    fi
                                '''
                            }
                        }
                    }
                }

                parallel tasks
            }
        }
    }

    stage('Test Changed Services') {
        when {
            expression {
                return !!env.BUILD_SERVICES?.trim()
            }
        }

        steps {
            script {
                def selected = env.BUILD_SERVICES.split(',')
                def tasks = [:]

                for (serviceName in selected) {
                    def service = serviceName.trim()

                    tasks[service] = {
                        stage("Test: ${service}") {
                            dir("services/${service}") {
                                sh '''
                                    set -eu

                                    if [ -f ./mvnw ]; then
                                        chmod +x ./mvnw
                                        ./mvnw -B test
                                    elif command -v mvn >/dev/null 2>&1; then
                                        mvn -B test
                                    else
                                        echo "ERROR: Maven or Maven wrapper is required."
                                        exit 1
                                    fi
                                '''
                            }
                        }
                    }
                }

                parallel tasks
            }
        }
    }
}

post {
    success {
        echo "SUCCESS: Pipeline completed. Services: ${env.BUILD_SERVICES ?: 'none'}"
    }

    failure {
        echo 'FAILED: Check checkout, change detection, build, or test logs.'
    }

    always {
        echo 'Pipeline finished.'
    }
}

}
