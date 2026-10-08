
pipeline {
    agent any

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
        GIT_BRANCH = 'main'
    }

    stages {
        stage('Checkout Source Code') {
            steps {
                checkout scm

                sh '''
                    git fetch origin \
                      +refs/heads/main:refs/remotes/origin/main
                '''

                echo 'Source code checked out successfully.'
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

                    def previous = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
                    def firstBuild = !previous?.trim()

                    if (firstBuild) {
                        echo 'First successful build not available. Validating all services.'
                        env.BUILD_SERVICES = services.join(',')
                    } else {
                        def validCommit = sh(
                            script: "git cat-file -e '${previous}^{commit}'",
                            returnStatus: true
                        ) == 0

                        if (!validCommit) {
                            echo 'Previous commit unavailable. Validating all services.'
                            env.BUILD_SERVICES = services.join(',')
                        } else {
                            def changed = sh(
                                script: "git diff --name-only ${previous} HEAD",
                                returnStdout: true
                            ).trim()

                            echo "Changed files:\n${changed}"

                            def files = changed
                                ? changed.readLines()
                                : []

                            def fullBuild = files.any { file ->
                                file == 'Jenkinsfile' ||
                                file.startsWith('shared-library/') ||
                                file == 'pom.xml' ||
                                file == 'settings.xml' ||
                                file.startsWith('.mvn/') ||
                                file == 'mvnw'
                            }

                            def selected = fullBuild
                                ? services
                                : services.findAll { service ->
                                    files.any { file ->
                                        file.startsWith("services/${service}/")
                                    }
                                }

                            env.BUILD_SERVICES = selected.join(',')

                            if (fullBuild) {
                                echo 'Shared build configuration changed. Validating all services.'
                            } else if (selected.isEmpty()) {
                                echo 'No microservice source changes detected.'
                            } else {
                                echo "Services selected: ${selected.join(', ')}"
                            }
                        }
                    }
                }
            }
        }

        stage('Build and Test Changed Services') {
            when {
                expression {
                    return env.BUILD_SERVICES?.trim()
                }
            }

            steps {
                script {
                    def selected = env.BUILD_SERVICES
                        .split(',')
                        .findAll { it?.trim() }

                    def tasks = [:]

                    for (serviceName in selected) {
                        def service = serviceName

                        tasks[service] = {
                            stage("Build and Test: ${service}") {
                                dir("services/${service}") {
                                    sh '''
                                        set -eu

                                        echo "Building and testing ${PWD}"

                                        if [ -f ./mvnw ]; then
                                            chmod +x ./mvnw
                                            ./mvnw -B clean verify
                                        elif command -v mvn >/dev/null 2>&1; then
                                            mvn -B clean verify
                                        else
                                            echo "ERROR: Maven is not installed and no Maven wrapper exists."
                                            exit 1
                                        fi
                                    '''
                                }
                            }
                        }
                    }

                    if (tasks) {
                        parallel tasks
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline completed successfully. Selected services: ${env.BUILD_SERVICES ?: 'none'}"
        }

        failure {
            echo 'Pipeline failed. Check the checkout, change detection, and Maven test logs.'
        }

        always {
            echo 'Jenkins pipeline execution finished.'
        }
    }
}