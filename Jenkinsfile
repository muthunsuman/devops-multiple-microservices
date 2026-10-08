
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
        GIT_REPO = 'https://github.com/muthunsuman/devops-multiple-microservices.git'
        GIT_BRANCH = 'main'
    }

    stages {
        stage('Checkout Source Code') {
            steps {
                // Explicit Git checkout; no checkout scm
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

                    def previous = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT

                    if (!previous?.trim()) {
                        previous = ''
                    }

                    def validPrevious = previous &&
                        sh(
                            script: "git cat-file -e '${previous}^{commit}'",
                            returnStatus: true
                        ) == 0

                    if (!validPrevious) {
                        echo 'No valid previous successful commit. Selecting all services.'
                        env.BUILD_SERVICES = 'api-gateway,auth-service,cart-service,inventory-service,notification-service,order-service,payment-service,product-service,shipping-service,user-service'
                    } else {
                        env.PREVIOUS_COMMIT = previous
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

                    if (env.BUILD_SERVICES?.trim()) {
                        echo 'Using full validation because no previous successful commit was available.'
                    } else {
                        def changed = sh(
                            script: "git diff --name-only ${env.PREVIOUS_COMMIT} ${env.CURRENT_COMMIT}",
                            returnStdout: true
                        ).trim()

                        echo "Changed files:\n${changed}"

                        def files = changed ? changed.readLines() : []

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

                        echo fullBuild
                            ? 'Shared configuration changed: validating all services.'
                            : "Selected services: ${selected ?: 'none'}"
                    }
                }
            }
        }

        stage('Build Changed Services') {
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

                    if (tasks) {
                        parallel tasks
                    } else {
                        echo 'No changed services. Skipping build.'
                    }
                }
            }
        }

        stage('Test Changed Services') {
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
                            stage("Test: ${service}") {
                                dir("services/${service}") {
                                    sh '''
                                        set -eu

                                        if [ -f ./mvnw ]; then
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

                    if (tasks) {
                        parallel tasks
                    } else {
                        echo 'No changed services. Skipping tests.'
                    }
                }
            }
        }
    }

    post {
        success {
            echo "SUCCESS: Build and test completed. Services: ${env.BUILD_SERVICES ?: 'none'}"
        }

        failure {
            echo 'FAILED: Checks the relevant checkout, build, or test stage.'
        }

        always {
            echo 'Pipeline finished.'
        }
    }
}