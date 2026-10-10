
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
        GIT_REPO      = 'https://github.com/muthunsuman/devops-multiple-microservices.git'
        GIT_BRANCH    = 'main'
        AWS_REGION    = ' ap-southeast-2'
        AWS_ACCOUNT_ID = '028610956643'
        DOCKERFILE    = 'docker/Dockerfile'
    }

    stages {
        stage('Checkout Source Code') {
            steps {
                deleteDir()

                git branch: "${GIT_BRANCH}",
                    url: "${GIT_REPO}"

                script {
                    env.CURRENT_COMMIT = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()

                    env.PREVIOUS_COMMIT = ''

                    def previous =
                        env.GIT_PREVIOUS_SUCCESSFUL_COMMIT?.trim()

                    if (previous) {
                        def valid = sh(
                            script: "git cat-file -e '${previous}^{commit}'",
                            returnStatus: true
                        ) == 0

                        if (valid) {
                            env.PREVIOUS_COMMIT = previous
                        }
                    }

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

                    env.IMAGE_TAG =
                        "${env.BUILD_NUMBER}-${env.CURRENT_COMMIT.take(7)}"

                    env.ECR_REGISTRY =
                        "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"

                    echo "Commit: ${env.CURRENT_COMMIT}"
                    echo "Previous commit: ${env.PREVIOUS_COMMIT ?: 'none'}"
                    echo "Image tag: ${env.IMAGE_TAG}"
                    echo "ECR registry: ${env.ECR_REGISTRY}"
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
                        def output = sh(
                            script: """
                                git diff --name-only \
                                  '${env.PREVIOUS_COMMIT}' \
                                  '${env.CURRENT_COMMIT}'
                            """,
                            returnStdout: true
                        ).trim()

                        changedFiles = output ? output.readLines() : []
                    } else {
                        def output = sh(
                            script: 'git ls-files',
                            returnStdout: true
                        ).trim()

                        changedFiles = output ? output.readLines() : []
                    }

                    echo "Changed files: ${changedFiles.join(', ')}"

                    def selected = services.findAll { service ->
                        fileExists("services/${service}") &&
                        changedFiles.any { file ->
                            file.startsWith("services/${service}/")
                        }
                    }

                    def sharedChanged = changedFiles.any { file ->
                        file == 'Jenkinsfile' ||
                        file == 'pom.xml' ||
                        file == 'mvnw' ||
                        file.startsWith('.mvn/') ||
                        file.startsWith('shared-library/') ||
                        file == 'docker/Dockerfile'
                    }

                    if (sharedChanged) {
                        selected = services.findAll { service ->
                            fileExists("services/${service}")
                        }

                        echo 'Shared build configuration changed.'
                    }

                    env.BUILD_SERVICES = selected.join(',')

                    echo "Selected services: ${
                        env.BUILD_SERVICES ?: 'none'
                    }"
                }
            }
        }

        stage('Validate AWS and Tools') {
            when {
                expression {
                    return !!env.BUILD_SERVICES?.trim()
                }
            }
            steps {
                sh '''
                    set -eu

                    command -v java
                    command -v mvn
                    command -v docker
                    command -v aws

                    java -version
                    mvn -version
                    docker --version
                    aws --version

                    ACTUAL_ACCOUNT=$(aws sts get-caller-identity \
                        --query Account --output text)

                    if [ "$ACTUAL_ACCOUNT" != "$AWS_ACCOUNT_ID" ]; then
                        echo "ERROR: Unexpected AWS account: $ACTUAL_ACCOUNT"
                        exit 1
                    fi

                    aws sts get-caller-identity
                '''
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
                                    else
                                        mvn -B -DskipTests package
                                    fi
                                '''
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
                                        ./mvnw -B test
                                    else
                                        mvn -B test
                                    fi
                                '''
                            }
                        }
                    }

                    parallel tasks
                }
            }
        }

        stage('Prepare ECR Repositories') {
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

                    selected.each { service ->
                        withEnv(["SERVICE_NAME=${service}"]) {
                            sh '''
                                set -eu

                                if ! aws ecr describe-repositories \
                                    --repository-names "$SERVICE_NAME" \
                                    --region "$AWS_REGION" \
                                    >/dev/null 2>&1; then

                                    aws ecr create-repository \
                                        --repository-name "$SERVICE_NAME" \
                                        --region "$AWS_REGION"
                                fi
                            '''
                        }
                    }
                }
            }
        }

        stage('Login to Amazon ECR') {
            when {
                expression {
                    return !!env.BUILD_SERVICES?.trim()
                }
            }
            steps {
                sh '''
                    set -eu

                    aws ecr get-login-password \
                        --region "$AWS_REGION" |
                    docker login \
                        --username AWS \
                        --password-stdin "$ECR_REGISTRY"
                '''
            }
        }

        stage('Build and Push Docker Images') {
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
                            withEnv(["SERVICE_NAME=${service}"]) {
                                sh '''
                                    set -eu

                                    SERVICE_DIR="services/$SERVICE_NAME"
                                    IMAGE="$ECR_REGISTRY/$SERVICE_NAME"

                                    if [ ! -d "$SERVICE_DIR/target" ] ||
                                       ! find "$SERVICE_DIR/target" \
                                           -maxdepth 1 -type f \
                                           -name '*.jar' \
                                           ! -name '*.original' \
                                           | grep -q .; then
                                        echo "ERROR: No packaged JAR for $SERVICE_NAME"
                                        exit 1
                                    fi

                                    if [ -f "$DOCKERFILE" ]; then
                                        BUILD_FILE="$DOCKERFILE"
                                    elif [ -f "$SERVICE_DIR/Dockerfile" ]; then
                                        BUILD_FILE="$SERVICE_DIR/Dockerfile"
                                    else
                                        echo "ERROR: No Dockerfile found"
                                        echo "Expected $DOCKERFILE or $SERVICE_DIR/Dockerfile"
                                        exit 1
                                    fi

                                    echo "Building image: $IMAGE:$IMAGE_TAG"

                                    docker build \
                                        --pull \
                                        -f "$BUILD_FILE" \
                                        -t "$IMAGE:$IMAGE_TAG" \
                                        -t "$IMAGE:latest" \
                                        "$SERVICE_DIR"

                                    docker push "$IMAGE:$IMAGE_TAG"
                                    docker push "$IMAGE:latest"

                                    echo "Successfully pushed $IMAGE:$IMAGE_TAG"
                                '''
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
            echo """
                SUCCESS: Pipeline completed.
                Services: ${env.BUILD_SERVICES ?: 'none'}
                Commit: ${env.CURRENT_COMMIT ?: 'unknown'}
                Image tag: ${env.IMAGE_TAG ?: 'none'}
            """
        }

        failure {
            echo 'FAILED: Check the stage logs for the error.'
        }

        always {
            echo 'Pipeline finished.'
        }
    }
}
