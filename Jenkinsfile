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

        stage('Checkout Source Code') {
            steps {
                deleteDir()

                // Explicit checkout: no checkout scm
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

                    env.PREVIOUS_COMMIT = ''

                    // Prefer the last successful commit if available.
                    def previous =
                        env.GIT_PREVIOUS_SUCCESSFUL_COMMIT?.trim()

                    if (previous) {
                        def validPrevious = sh(
                            script: "git cat-file -e '${previous}^{commit}'",
                            returnStatus: true
                        ) == 0

                        if (validPrevious) {
                            env.PREVIOUS_COMMIT = previous
                        }
                    }

                    // Fallback for a first build or missing previous commit.
                    if (!env.PREVIOUS_COMMIT) {
                        def parentStatus = sh(
                            script: 'git rev-parse HEAD^',
                            returnStatus: true
                        )

                        if (parentStatus == 0) {
                            env.PREVIOUS_COMMIT = sh(
                                script: 'git rev-parse HEAD^',
                                returnStdout: true
                            ).trim()
                        }
                    }

                    echo "Current commit: ${env.CURRENT_COMMIT}"
                    echo "Comparison base: ${
                        env.PREVIOUS_COMMIT ?: 'none; first-build scan'
                    }"
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

                    def changedFiles = []

                    if (env.PREVIOUS_COMMIT) {
                        // If comparison fails, fail safely rather than
                        // silently skipping changes.
                        def diffOutput = sh(
                            script: """
                                git diff --name-only \
                                  '${env.PREVIOUS_COMMIT}' \
                                  '${env.CURRENT_COMMIT}'
                            """,
                            returnStdout: true
                        ).trim()

                        changedFiles = diffOutput
                            ? diffOutput.readLines()
                            : []

                        echo "Changed files:\\n${
                            changedFiles ? changedFiles.join('\\n') : 'None'
                        }"
                    } else {
                        // First build: inspect all tracked service files.
                        def trackedOutput = sh(
                            script: 'git ls-files services/',
                            returnStdout: true
                        ).trim()

                        changedFiles = trackedOutput
                            ? trackedOutput.readLines()
                            : []

                        echo 'No comparison base found; scanning all services.'
                    }

                    def affected = services.findAll { service ->
                        changedFiles.any { file ->
                            file.startsWith("services/${service}/")
                        }
                    }

                    // Only build services that still exist in this checkout.
                    def selected = affected.findAll { service ->
                        fileExists("services/${service}")
                    }

                    def deleted = affected - selected

                    if (deleted) {
                        echo "Removed service directories; skipping build: ${
                            deleted.join(', ')
                        }"
                    }

                    // If shared build files change, use a full service build.
                    def sharedFilesChanged = changedFiles.any { file ->
                        !file.startsWith('services/') &&
                        (
                            file == 'pom.xml' ||
                            file == 'mvnw' ||
                            file.startsWith('.mvn/') ||
                            file.startsWith('shared-library/') ||
                            file == 'Jenkinsfile'
                        )
                    }

                    if (sharedFilesChanged) {
                        echo 'Shared build configuration changed; selecting all existing services.'

                        selected = services.findAll { service ->
                            fileExists("services/${service}")
                        }
                    }

                    env.BUILD_SERVICES = selected.join(',')

                    if (selected) {
                        echo "Services selected: ${env.BUILD_SERVICES}"
                    } else {
                        echo 'No applicable service changes. Build and test stages will be skipped.'
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
                    def selected = env.BUILD_SERVICES
                        .split(',')
                        .collect { it.trim() }
                        .findAll { it }

                    def tasks = [:]

                    selected.each { serviceName ->
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

                    echo "Building in parallel: ${selected.join(', ')}"
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
                    def selected = env.BUILD_SERVICES
                        .split(',')
                        .collect { it.trim() }
                        .findAll { it }

                    def tasks = [:]

                    selected.each { serviceName ->
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

                    echo "Testing in parallel: ${selected.join(', ')}"
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
                Commit: ${env.CURRENT_COMMIT ?: 'unknown'}
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

