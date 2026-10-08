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
        GIT_REPO   = 'https://github.com/muthunsuman/devops-multiple-microservices.git'
        GIT_BRANCH = 'main'
    }

    stages {

        // 1. CHECKOUT SOURCE CODE
        stage('Checkout Source Code') {
            steps {
                deleteDir()

                git branch: "${GIT_BRANCH}",
                    url: "${GIT_REPO}"

                sh '''
                    set -eu
                    git fetch origin \
                      +refs/heads/main:refs/remotes/origin/main
                '''

                script {
                    env.CURRENT_COMMIT = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()

                    // Find the previous successful build commit.
                    def previous =
                        env.GIT_PREVIOUS_SUCCESSFUL_COMMIT?.trim()

                    if (previous) {
                        env.PREVIOUS_COMMIT = previous
                    } else {
                        // First-build fallback.
                        def parentStatus = sh(
                            script: 'git rev-parse HEAD^',
                            returnStatus: true
                        )

                        if (parentStatus == 0) {
                            env.PREVIOUS_COMMIT = sh(
                                script: 'git rev-parse HEAD^',
                                returnStdout: true
                            ).trim()
                        } else {
                            env.PREVIOUS_COMMIT = ''
                        }
                    }

                    env.CHANGE_BASE_FOUND =
                        env.PREVIOUS_COMMIT ? 'true' : 'false'

                    echo "Current commit: ${env.CURRENT_COMMIT}"
                    echo "Previous commit: ${env.PREVIOUS_COMMIT ?: 'none'}"
                }
            }
        }

        // 2. DETECT CHANGED SERVICES
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

                    def selected = []

                    if (env.CHANGE_BASE_FOUND == 'true') {
                        sh """
                            git cat-file -e \
                              '${env.PREVIOUS_COMMIT}^{commit}'
                        """

                        def changed = sh(
                            script: """
                                git diff --name-only \
                                  '${env.PREVIOUS_COMMIT}' \
                                  '${env.CURRENT_COMMIT}'
                            """,
                            returnStdout: true
                        ).trim()

                        echo "Changed files:\n${changed ?: 'None'}"

                        def files = changed
                            ? changed.readLines()
                            : []

                        selected = services.findAll { service ->
                            files.any { file ->
                                file.startsWith("services/${service}/")
                            }
                        }

                    } else {
                        // First build: select services containing tracked files.
                        def tracked = sh(
                            script: 'git ls-files services/',
                            returnStdout: true
                        ).trim()

                        def files = tracked
                            ? tracked.readLines()
                            : []

                        selected = services.findAll { service ->
                            files.any { file ->
                                file.startsWith("services/${service}/")
                            }
                        }

                        echo 'First build: selecting existing services.'
                    }

                    env.BUILD_SERVICES = selected.join(',')

                    if (selected) {
                        echo "Selected services: ${env.BUILD_SERVICES}"
                    } else {
                        echo 'No service changes detected. Build and test will be skipped.'
                    }
                }
            }
        }

        // 3. BUILD CHANGED SERVICES IN PARALLEL
        // One visible stage; no nested stage() calls.
        stage('Build Changed Services') {
            when {
                expression {
                    return !!env.BUILD_SERVICES?.trim()
                }
            }

            steps {
                script {
                    def selected = env.BUILD_SERVICES
                        .split(',')
                        .collect { it.trim() }
                        .findAll { it }

                    def tasks = [:]

                    for (serviceName in selected) {
                        def service = serviceName

                        tasks[service] = {
                            dir("services/${service}") {
                                echo "Building ${service}"

                                sh '''
                                    set -eu

                                    if [ -f ./mvnw ]; then
                                        chmod +x ./mvnw
                                        ./mvnw -B -DskipTests package

                                    elif command -v mvn >/dev/null 2>&1; then
                                        mvn -B -DskipTests package

                                    else
                                        echo "ERROR: Maven or mvnw is required."
                                        exit 1
                                    fi
                                '''
                            }
                        }
                    }

                    echo "Starting parallel builds: ${selected.join(', ')}"
                    parallel tasks
                }
            }
        }

        // 4. TEST CHANGED SERVICES IN PARALLEL
        // One visible stage; tests start after all builds succeed.
        stage('Test Changed Services') {
            when {
                expression {
                    return !!env.BUILD_SERVICES?.trim()
                }
            }

            steps {
                script {
                    def selected = env.BUILD_SERVICES
                        .split(',')
                        .collect { it.trim() }
                        .findAll { it }

                    def tasks = [:]

                    for (serviceName in selected) {
                        def service = serviceName

                        tasks[service] = {
                            dir("services/${service}") {
                                echo "Testing ${service}"

                                sh '''
                                    set -eu

                                    if [ -f ./mvnw ]; then
                                        chmod +x ./mvnw
                                        ./mvnw -B test

                                    elif command -v mvn >/dev/null 2>&1; then
                                        mvn -B test

                                    else
                                        echo "ERROR: Maven or mvnw is required."
                                        exit 1
                                    fi
                                '''
                            }
                        }
                    }

                    echo "Starting parallel tests: ${selected.join(', ')}"
                    parallel tasks
                }
            }
        }
    }

    post {
        success {
            echo """
                SUCCESS: Pipeline completed.
                Services processed: ${env.BUILD_SERVICES ?: 'none'}
            """
        }

        failure {
            echo 'FAILED: Check checkout, change detection, build, or test logs.'
        }

        always {
            echo 'Pipeline finished.'
        }
    }
}

