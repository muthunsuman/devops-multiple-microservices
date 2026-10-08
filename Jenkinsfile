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

                    // Use the previous successful build's commit when available.
                    // This helps detect changes across multiple commits.
                    def previous = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT?.trim()

                    if (previous) {
                        env.PREVIOUS_COMMIT = previous
                    } else {
                        // Fallback for the first build.
                        def parentResult = sh(
                            script: 'git rev-parse HEAD^',
                            returnStatus: true
                        )

                        if (parentResult == 0) {
                            env.PREVIOUS_COMMIT = sh(
                                script: 'git rev-parse HEAD^',
                                returnStdout: true
                            ).trim()
                        } else {
                            env.PREVIOUS_COMMIT = ''
                        }
                    }

                    if (env.PREVIOUS_COMMIT) {
                        env.CHANGE_BASE_FOUND = 'true'
                        echo "Comparison base: ${env.PREVIOUS_COMMIT}"
                        echo "Current commit: ${env.CURRENT_COMMIT}"
                    } else {
                        env.CHANGE_BASE_FOUND = 'false'
                        echo 'No previous commit available for comparison.'
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

                    def selected = []

                    if (env.CHANGE_BASE_FOUND == 'true') {
                        // Ensure the comparison commit is available locally.
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

                        echo "Changed files:\\n${changed ?: 'No files changed'}"

                        def files = changed
                            ? changed.readLines()
                            : []

                        selected = services.findAll { service ->
                            files.any { file ->
                                file.startsWith("services/${service}/")
                            }
                        }
                    } else {
                        // First build: inspect the repository for service files.
                        // Build only services that actually contain tracked files.
                        def files = sh(
                            script: 'git ls-files services/',
                            returnStdout: true
                        ).trim()

                        def trackedFiles = files
                            ? files.readLines()
                            : []

                        selected = services.findAll { service ->
                            trackedFiles.any { file ->
                                file.startsWith("services/${service}/")
                            }
                        }

                        echo 'First build: selecting services containing tracked files.'
                    }

                    env.BUILD_SERVICES = selected.join(',')

                    if (selected) {
                        echo "Services selected: ${selected.join(', ')}"
                    } else {
                        echo 'No service directories changed. Skipping service builds and tests.'
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
                                            echo "ERROR: Maven or mvnw is required."
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
                                            echo "ERROR: Maven or mvnw is required."
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
