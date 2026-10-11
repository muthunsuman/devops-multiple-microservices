
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
        // Verify these values against your actual AWS resources.
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

                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${env.CURRENT_COMMIT.take(7)}"
                    env.ECR_REGISTRY = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"

                    echo "Current commit: ${env.CURRENT_COMMIT}"
                    echo "Previous commit: ${env.PREVIOUS_COMMIT ?: 'none'}"
                    echo "AWS region: ${env.AWS_REGION}"
                    echo "ECR repository: ${env.ECR_REPOSITORY}"
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

                    echo "Changed files: ${changedFiles.join(', ')}"

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
                        file == 'docker/Dockerfile'
                    }

                    if (sharedChanged) {
                        selected = services.findAll { service ->
                            fileExists("services/${service}")
                        }

                        echo 'Shared build configuration changed; selecting all existing services.'
                    }

                    env.BUILD_SERVICES = selected.join(',')

                    if (selected) {
                        echo "Services selected: ${env.BUILD_SERVICES}"
                    } else {
                        echo 'No relevant microservice or shared build changes detected.'
                    }
                }
            }
        }

        stage('Validate Tools and AWS Identity') {
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

                    ACTUAL_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)

                    if [ "$ACTUAL_ACCOUNT" != "$AWS_ACCOUNT_ID" ]; then
                        echo "ERROR: Expected AWS account $AWS_ACCOUNT_ID but got $ACTUAL_ACCOUNT"
                        exit 1
                    fi

                    aws sts get-caller-identity
                '''
            }
        }

        stage('Verify Existing ECR Repository') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                sh '''
                    set -eu

                    aws ecr describe-repositories \
                        --repository-names "$ECR_REPOSITORY" \
                        --region "$AWS_REGION"

                    echo "Verified existing ECR repository."
                '''
            }
        }

        stage('Build Changed Services') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                script {
                    def selected = env.BUILD_SERVICES.split(',').findAll { it.trim() }
                    def tasks = [:]

                    selected.each { serviceName ->
                        def service = serviceName.trim()

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
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                script {
                    def selected = env.BUILD_SERVICES.split(',').findAll { it.trim() }
                    def tasks = [:]

                    selected.each { serviceName ->
                        def service = serviceName.trim()

                        tasks[service] = {
                            dir("services/${service}") {
                                echo "Testing ${service}"

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

        stage('Login to Existing ECR') {
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

        stage('Build and Push Changed Service Images') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                script {
                    def selected = env.BUILD_SERVICES.split(',').findAll { it.trim() }
                    def tasks = [:]

                    selected.each { serviceName ->
                        def service = serviceName.trim()

                        tasks[service] = {
                            withEnv(["SERVICE_NAME=${service}"]) {
                                sh '''
                                    set -eu

                                    SERVICE_DIR="services/$SERVICE_NAME"
                                    IMAGE="$ECR_REGISTRY/$ECR_REPOSITORY"

                                    if [ ! -d "$SERVICE_DIR/target" ]; then
                                        echo "ERROR: Build output directory missing for $SERVICE_NAME"
                                        exit 1
                                    fi

                                    JAR=$(find "$SERVICE_DIR/target" -maxdepth 1 -type f \
                                        -name '*.jar' ! -name '*.original' -print -quit)

                                    if [ -z "$JAR" ]; then
                                        echo "ERROR: No packaged JAR found for $SERVICE_NAME"
                                        exit 1
                                    fi

                                    if [ ! -f "$DOCKERFILE" ]; then
                                        echo "ERROR: Dockerfile not found: $DOCKERFILE"
                                        exit 1
                                    fi

                                    # Use a unique versioned tag because ECR tags are immutable.
                                    VERSIONED_IMAGE="$IMAGE:$SERVICE_NAME-$IMAGE_TAG"

                                    # Use the service directory as the build context so target/*.jar
                                    # can be copied by the Dockerfile.
                                    docker build --pull \
                                        -f "$DOCKERFILE" \
                                        -t "$VERSIONED_IMAGE" \
                                        "$SERVICE_DIR"

                                    docker push "$VERSIONED_IMAGE"

                                    echo "Pushed $VERSIONED_IMAGE"
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

        stage('Deploy Changed Services to EKS') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                script {
                    def selected = env.BUILD_SERVICES.split(',').findAll { it.trim() }
                    def tasks = [:]

                    selected.each { serviceName ->
                        def service = serviceName.trim()

                        tasks[service] = {
                            withEnv(["SERVICE_NAME=${service}"]) {
                                sh '''
                                    set -eu

                                    DEPLOYMENT="deployment/$SERVICE_NAME"
                                    IMAGE="$ECR_REGISTRY/$ECR_REPOSITORY:$SERVICE_NAME-$IMAGE_TAG"

                                    kubectl get "$DEPLOYMENT" -n "$K8S_NAMESPACE"

                                    # Assumes Kubernetes container name equals service name.
                                    kubectl set image "$DEPLOYMENT" \
                                        "$SERVICE_NAME=$IMAGE" \
                                        -n "$K8S_NAMESPACE"

                                    if ! kubectl rollout status "$DEPLOYMENT" \
                                        -n "$K8S_NAMESPACE" --timeout=5m; then

                                        echo "Rollout failed for $SERVICE_NAME; attempting rollback."

                                        kubectl rollout undo "$DEPLOYMENT" \
                                            -n "$K8S_NAMESPACE"

                                        kubectl rollout status "$DEPLOYMENT" \
                                            -n "$K8S_NAMESPACE" --timeout=5m || true

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

                    echo "Deployment status:"
                    kubectl get deployments -n "$K8S_NAMESPACE"

                    echo "Pod status:"
                    kubectl get pods -n "$K8S_NAMESPACE"

                    echo "Service status:"
                    kubectl get services -n "$K8S_NAMESPACE"

                    for service in $(echo "$BUILD_SERVICES" | tr ',' ' '); do
                        kubectl rollout status "deployment/$service" \
                            -n "$K8S_NAMESPACE" --timeout=5m
                    done
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
            echo 'Pipeline failed. Check the first failed stage and its console logs.'
        }

        always {
            echo 'Pipeline finished.'
        }
    }
}
