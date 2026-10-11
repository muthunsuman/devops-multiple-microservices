
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
        GIT_REPO       = 'https://github.com/muthunsuman/devops-multiple-microservices.git'
        GIT_BRANCH     = 'main'
        AWS_REGION     = 'ap-southeast-2'
        AWS_ACCOUNT_ID = '028610956643'
        EKS_CLUSTER    = 'devops-eks-cluster'
        K8S_NAMESPACE  = 'dev'
        ECR_REPOSITORY = 'devopsmultiplemicroservice'
        DOCKERFILE     = 'docker/Dockerfile'
    }

    stages {
        stage('Checkout Source Code') {
            steps {
                deleteDir()
                git branch: "${GIT_BRANCH}", url: "${GIT_REPO}"

                script {
                    env.CURRENT_COMMIT = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()

                    env.PREVIOUS_COMMIT = ''
                    def previous = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT?.trim()

                    if (previous) {
                        if (sh(
                            script: "git cat-file -e '${previous}^{commit}'",
                            returnStatus: true
                        ) == 0) {
                            env.PREVIOUS_COMMIT = previous
                        }
                    }

                    if (!env.PREVIOUS_COMMIT &&
                        sh(script: 'git rev-parse HEAD^', returnStatus: true) == 0) {
                        env.PREVIOUS_COMMIT = sh(
                            script: 'git rev-parse HEAD^',
                            returnStdout: true
                        ).trim()
                    }

                    env.IMAGE_TAG =
                        "${env.BUILD_NUMBER}-${env.CURRENT_COMMIT.take(7)}"

                    env.ECR_REGISTRY =
                        "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"

                    echo "Commit: ${env.CURRENT_COMMIT}"
                    echo "Previous commit: ${env.PREVIOUS_COMMIT ?: 'none'}"
                    echo "Image tag: ${env.IMAGE_TAG}"
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
                            script: "git diff --name-only '${env.PREVIOUS_COMMIT}' '${env.CURRENT_COMMIT}'",
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

                    def selected = services.findAll { service ->
                        fileExists("services/${service}") &&
                        changedFiles.any { file ->
                            file.startsWith("services/${service}/")
                        }
                    }

                    def sharedChanged = changedFiles.any { file ->
                        file == 'pom.xml' ||
                        file == 'mvnw' ||
                        file.startsWith('.mvn/') ||
                        file.startsWith('shared-library/') ||
                        file.startsWith('docker/')
                    }

                    if (sharedChanged) {
                        selected = services.findAll { service ->
                            fileExists("services/${service}")
                        }
                        echo 'Shared files changed; selecting all services.'
                    }

                    env.BUILD_SERVICES = selected.join(',')

                    if (selected) {
                        echo "Selected services: ${env.BUILD_SERVICES}"
                    } else {
                        echo 'No service changes detected.'
                    }
                }
            }
        }

        stage('Validate Tools and AWS') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                sh '''
                    set -eu

                    command -v java
                    command -v mvn
                    command -v docker
                    command -v aws
                    command -v kubectl

                    java -version
                    mvn -version
                    docker --version
                    aws --version
                    kubectl version --client

                    ACTUAL_ACCOUNT=$(aws sts get-caller-identity \
                        --query Account --output text)

                    if [ "$ACTUAL_ACCOUNT" != "$AWS_ACCOUNT_ID" ]; then
                        echo "ERROR: Expected AWS account $AWS_ACCOUNT_ID, got $ACTUAL_ACCOUNT"
                        exit 1
                    fi

                    aws sts get-caller-identity
                    docker system df
                '''
            }
        }

        stage('Verify ECR Repositories') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                sh '''
                    set -eu

                    for service in $(echo "$BUILD_SERVICES" | tr ',' ' '); do
                        REPO="$ECR_REPOSITORY/$service"

                        echo "Checking ECR repository: $REPO"

                        aws ecr describe-repositories \
                            --repository-names "$REPO" \
                            --region "$AWS_REGION"
                    done
                '''
            }
        }

        stage('Build Changed Services') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                script {
                    def tasks = [:]

                    env.BUILD_SERVICES.split(',').findAll { it.trim() }.each {
                        serviceName ->
                        def service = serviceName.trim()

                        tasks[service] = {
                            dir("services/${service}") {
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
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                script {
                    def tasks = [:]

                    env.BUILD_SERVICES.split(',').findAll { it.trim() }.each {
                        serviceName ->
                        def service = serviceName.trim()

                        tasks[service] = {
                            dir("services/${service}") {
                                sh '''
                                    set -eu

                                    if [ -f ./mvnw ]; then
                                        chmod +x ./mvnw
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

        stage('Login to ECR') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                sh '''
                    set -eu

                    aws ecr get-login-password --region "$AWS_REGION" |
                        docker login --username AWS --password-stdin "$ECR_REGISTRY"
                '''
            }
        }

        stage('Build and Push Images') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                script {
                    def tasks = [:]

                    env.BUILD_SERVICES.split(',').findAll { it.trim() }.each {
                        serviceName ->
                        def service = serviceName.trim()

                        tasks[service] = {
                            withEnv(["SERVICE_NAME=${service}"]) {
                                sh '''
                                    set -eu

                                    SERVICE_DIR="services/$SERVICE_NAME"
                                    ECR_IMAGE="$ECR_REGISTRY/$ECR_REPOSITORY/$SERVICE_NAME"
                                    VERSIONED_IMAGE="$ECR_IMAGE:$IMAGE_TAG"

                                    if [ ! -d "$SERVICE_DIR/target" ]; then
                                        echo "ERROR: Missing $SERVICE_DIR/target"
                                        exit 1
                                    fi

                                    JAR=$(find "$SERVICE_DIR/target" \
                                        -maxdepth 1 -type f \
                                        -name '*.jar' \
                                        ! -name '*.original' \
                                        -print -quit)

                                    if [ -z "$JAR" ]; then
                                        echo "ERROR: No packaged JAR for $SERVICE_NAME"
                                        exit 1
                                    fi

                                    if [ ! -f "$DOCKERFILE" ]; then
                                        echo "ERROR: Dockerfile not found: $DOCKERFILE"
                                        exit 1
                                    fi

                                    echo "Building image: $VERSIONED_IMAGE"

                                    docker build --pull \
                                        -f "$DOCKERFILE" \
                                        -t "$VERSIONED_IMAGE" \
                                        "$SERVICE_DIR"

                                    docker push "$VERSIONED_IMAGE"

                                    echo "Pushed image: $VERSIONED_IMAGE"
                                '''
                            }
                        }
                    }

                    parallel tasks
                }
            }
        }

        stage('Configure EKS Access') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                sh '''
                    set -eu

                    aws eks describe-cluster \
                        --name "$EKS_CLUSTER" \
                        --region "$AWS_REGION"

                    aws eks update-kubeconfig \
                        --region "$AWS_REGION" \
                        --name "$EKS_CLUSTER"

                    kubectl cluster-info
                    kubectl get namespace "$K8S_NAMESPACE"
                '''
            }
        }

        stage('Deploy Changed Services') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                script {
                    def tasks = [:]

                    env.BUILD_SERVICES.split(',').findAll { it.trim() }.each {
                        serviceName ->
                        def service = serviceName.trim()

                        tasks[service] = {
                            withEnv(["SERVICE_NAME=${service}"]) {
                                sh '''
                                    set -eu

                                    DEPLOYMENT="deployment/$SERVICE_NAME"
                                    IMAGE="$ECR_REGISTRY/$ECR_REPOSITORY/$SERVICE_NAME:$IMAGE_TAG"

                                    kubectl get "$DEPLOYMENT" -n "$K8S_NAMESPACE"

                                    if ! kubectl get "$DEPLOYMENT" \
                                        -n "$K8S_NAMESPACE" \
                                        -o jsonpath='{.spec.template.spec.containers[*].name}' |
                                        tr ' ' '\\n' | grep -Fxq "$SERVICE_NAME"; then

                                        echo "ERROR: Container '$SERVICE_NAME' not found in $DEPLOYMENT"
                                        exit 1
                                    fi

                                    echo "Deploying image: $IMAGE"

                                    kubectl set image "$DEPLOYMENT" \
                                        "$SERVICE_NAME=$IMAGE" \
                                        -n "$K8S_NAMESPACE"

                                    if ! kubectl rollout status "$DEPLOYMENT" \
                                        -n "$K8S_NAMESPACE" \
                                        --timeout=5m; then

                                        echo "ERROR: Rollout failed for $SERVICE_NAME"
                                        kubectl get pods -n "$K8S_NAMESPACE" -o wide
                                        kubectl describe "$DEPLOYMENT" -n "$K8S_NAMESPACE"
                                        kubectl get events -n "$K8S_NAMESPACE" \
                                            --sort-by=.metadata.creationTimestamp
                                        exit 1
                                    fi

                                    echo "Deployment successful: $SERVICE_NAME"
                                '''
                            }
                        }
                    }

                    parallel tasks
                }
            }
        }

        stage('Verify Kubernetes Deployment') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                sh '''
                    set -eu

                    kubectl get deployments -n "$K8S_NAMESPACE"
                    kubectl get pods -n "$K8S_NAMESPACE" -o wide
                    kubectl get services -n "$K8S_NAMESPACE"

                    for service in $(echo "$BUILD_SERVICES" | tr ',' ' '); do
                        kubectl rollout status "deployment/$service" \
                            -n "$K8S_NAMESPACE" \
                            --timeout=5m
                    done
                '''
            }
        }

        stage('Clean Unused Docker Data') {
            steps {
                sh '''
                    set -eu

                    echo "Docker disk usage before cleanup:"
                    docker system df

                    # Remove dangling images only.
                    docker image prune -f

                    # Remove build cache older than seven days.
                    docker builder prune -f --filter 'until=168h'

                    echo "Docker disk usage after cleanup:"
                    docker system df
                '''
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully.'
            echo "Services built/deployed: ${env.BUILD_SERVICES ?: 'none'}"
            echo "Commit: ${env.CURRENT_COMMIT ?: 'unknown'}"
        }

        failure {
            echo 'Pipeline failed. Check the first failed stage and console output.'
        }

        always {
            echo 'Pipeline finished.'
        }
    }
}
