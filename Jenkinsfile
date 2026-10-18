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
        GIT_REPO             = 'https://github.com/muthunsuman/devops-multiple-microservices.git'
        GIT_BRANCH           = 'main'
        AWS_REGION           = 'ap-southeast-2'
        AWS_ACCOUNT_ID       = '028610956643'
        EKS_CLUSTER          = 'devops-eks-cluster'
        K8S_NAMESPACE        = 'dev'
        ECR_REPOSITORY       = 'devopsmultiplemicroservice'
        DOCKERFILE           = 'docker/Dockerfile'
        SONAR_HOST_URL       = 'http://13.211.90.188:9000'
        SONAR_CREDENTIALS_ID = 'sonarqube-token'
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                git branch: "${GIT_BRANCH}", url: "${GIT_REPO}"

                script {
                    env.CURRENT_COMMIT = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()

                    env.ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                    env.IMAGE_TAG = "${BUILD_NUMBER}-${env.CURRENT_COMMIT.take(7)}"

                    env.PREVIOUS_COMMIT = sh(
                        script: '''
                            if git rev-parse HEAD^ >/dev/null 2>&1; then
                                git rev-parse HEAD^
                            else
                                echo ""
                            fi
                        ''',
                        returnStdout: true
                    ).trim()

                    echo "Commit: ${env.CURRENT_COMMIT}"
                    echo "Image tag: ${env.IMAGE_TAG}"
                    echo "SonarQube URL configured as: ${env.SONAR_HOST_URL}"
                }
            }
        }

        stage('Detect Changed Services') {
            steps {
                script {
                    def services = [
                        'api-gateway', 'auth-service', 'cart-service',
                        'inventory-service', 'notification-service',
                        'order-service', 'payment-service', 'product-service',
                        'shipping-service', 'user-service'
                    ]

                    def changedFiles = []

                    if (env.PREVIOUS_COMMIT) {
                        def output = sh(
                            script: "git diff --name-only '${env.PREVIOUS_COMMIT}' '${env.CURRENT_COMMIT}'",
                            returnStdout: true
                        ).trim()
                        changedFiles = output ? output.readLines() : []
                    } else {
                        changedFiles = services.collectMany { service ->
                            def output = sh(
                                script: "git ls-files 'services/${service}/'",
                                returnStdout: true
                            ).trim()
                            output ? output.readLines() : []
                        }
                    }

                    boolean sharedChanged = changedFiles.any { file ->
                        file == 'pom.xml' ||
                        file == 'mvnw' ||
                        file.startsWith('.mvn/') ||
                        file.startsWith('shared-library/') ||
                        file.startsWith('docker/')
                    }

                    def selected = services.findAll { service ->
                        fileExists("services/${service}") &&
                        changedFiles.any { file ->
                            file.startsWith("services/${service}/")
                        }
                    }

                    if (sharedChanged) {
                        selected = services.findAll { service ->
                            fileExists("services/${service}")
                        }
                        echo 'Shared build files changed: selecting all services.'
                    }

                    env.BUILD_SERVICES = selected.join(',')
                    echo "Changed files: ${changedFiles}"
                    echo "Services selected: ${env.BUILD_SERVICES ?: 'none'}"

                    if (!env.BUILD_SERVICES) {
                        echo 'No service source changes detected.'
                    }
                }
            }
        }

        stage('Validate Tools') {
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
                    command -v curl

                    java -version
                    mvn -version
                    docker --version
                    aws --version

                    ACTUAL_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
                    if [ "$ACTUAL_ACCOUNT" != "$AWS_ACCOUNT_ID" ]; then
                        echo "ERROR: Expected AWS account $AWS_ACCOUNT_ID; got $ACTUAL_ACCOUNT"
                        exit 1
                    fi

                    echo "Tools and AWS account verified."
                '''
            }
        }

        stage('Build and Test (Maven)') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                script {
                    def tasks = [:]
                    env.BUILD_SERVICES.split(',').each { serviceName ->
                        def service = serviceName.trim()
                        tasks[service] = {
                            dir("services/${service}") {
                                sh '''
                                    set -eu
                                    if [ -f ./mvnw ]; then
                                        chmod +x ./mvnw
                                        ./mvnw -B clean verify
                                    else
                                        mvn -B clean verify
                                    fi
                                '''
                            }
                        }
                    }
                    parallel tasks
                }
            }
        }

        stage('Test SonarQube Connection') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                sh '''
                    set -eu
                    echo "Testing SonarQube URL: $SONAR_HOST_URL"
                    curl -fsS --connect-timeout 10 --max-time 20 \
                        "$SONAR_HOST_URL/api/system/status"
                    echo
                '''
            }
        }

        
stage('SonarQube Analysis and Quality Gate') {
    when {
        expression {
            return !!env.BUILD_SERVICES?.trim()
        }
    }
    steps {
        script {
            env.BUILD_SERVICES.split(',').each { serviceName ->
                def service = serviceName.trim()

                withEnv(["SERVICE_NAME=${service}"]) {
                    dir("services/${service}") {
                        withSonarQubeEnv('SonarQubeServer') {
                            withCredentials([
                                string(
                                    credentialsId: 'sonarqube-token',
                                    variable: 'SONAR_TOKEN'
                                )
                            ]) {
                                sh '''
                                    set -eu

                                    mvn -B \
                                      org.sonarsource.scanner.maven:sonar-maven-plugin:5.3.0.6276:sonar \
                                      -Dsonar.host.url="$SONAR_HOST_URL" \
                                      -Dsonar.token="$SONAR_TOKEN" \
                                      -Dsonar.projectKey="$SERVICE_NAME" \
                                      -Dsonar.projectName="$SERVICE_NAME"
                                '''
                            }
                        }
                    }
                }

                timeout(time: 10, unit: 'MINUTES') {
                    def qg = waitForQualityGate()
                    echo "Quality Gate for ${service}: ${qg.status}"

                    if (qg.status != 'OK') {
                        error("Quality Gate failed for ${service}: ${qg.status}")
                    }
                }
            }
        }
    }
}
        stage('Ensure ECR Repository and Login') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                sh '''
                    set -eu
                    if ! aws ecr describe-repositories \
                        --repository-names "$ECR_REPOSITORY" \
                        --region "$AWS_REGION" >/dev/null 2>&1; then
                        aws ecr create-repository \
                            --repository-name "$ECR_REPOSITORY" \
                            --image-tag-mutability MUTABLE \
                            --region "$AWS_REGION"
                    fi

                    aws ecr get-login-password --region "$AWS_REGION" |
                        docker login --username AWS --password-stdin "$ECR_REGISTRY"
                '''
            }
        }

        stage('Build and Push Docker Images to ECR') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                script {
                    def tasks = [:]
                    env.BUILD_SERVICES.split(',').each { serviceName ->
                        def service = serviceName.trim()
                        tasks[service] = {
                            withEnv(["SERVICE_NAME=${service}"]) {
                                sh '''
                                    set -eu
                                    SERVICE_DIR="services/$SERVICE_NAME"
                                    ECR_IMAGE="$ECR_REGISTRY/$ECR_REPOSITORY"
                                    VERSIONED_IMAGE="$ECR_IMAGE:$SERVICE_NAME-$IMAGE_TAG"
                                    LATEST_IMAGE="$ECR_IMAGE:$SERVICE_NAME-latest"

                                    test -f "$DOCKERFILE" || {
                                        echo "ERROR: Dockerfile not found: $DOCKERFILE"
                                        exit 1
                                    }
                                    test -d "$SERVICE_DIR/target" || {
                                        echo "ERROR: Maven target directory missing for $SERVICE_NAME"
                                        exit 1
                                    }

                                    JAR=$(find "$SERVICE_DIR/target" -maxdepth 1 -type f \
                                        -name '*.jar' ! -name '*.original' -print -quit)
                                    test -n "$JAR" || {
                                        echo "ERROR: No packaged JAR for $SERVICE_NAME"
                                        exit 1
                                    }

                                    docker build --pull \
                                        -f "$DOCKERFILE" \
                                        -t "$VERSIONED_IMAGE" \
                                        -t "$LATEST_IMAGE" \
                                        "$SERVICE_DIR"

                                    docker push "$VERSIONED_IMAGE"
                                    docker push "$LATEST_IMAGE"
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
                    command -v kubectl

                    aws eks describe-cluster --name "$EKS_CLUSTER" --region "$AWS_REGION" >/dev/null
                    aws eks update-kubeconfig --region "$AWS_REGION" --name "$EKS_CLUSTER"
                    kubectl cluster-info

                    if ! kubectl get namespace "$K8S_NAMESPACE" >/dev/null 2>&1; then
                        kubectl create namespace "$K8S_NAMESPACE"
                    fi
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
                    env.BUILD_SERVICES.split(',').each { serviceName ->
                        def service = serviceName.trim()
                        tasks[service] = {
                            withEnv(["SERVICE_NAME=${service}"]) {
                                sh '''
                                    set -eu
                                    DEPLOYMENT="deployment/$SERVICE_NAME"
                                    IMAGE="$ECR_REGISTRY/$ECR_REPOSITORY:$SERVICE_NAME-$IMAGE_TAG"

                                    if ! kubectl get "$DEPLOYMENT" -n "$K8S_NAMESPACE" >/dev/null 2>&1; then
                                        MANIFEST="gitops/environments/dev/services/$SERVICE_NAME/deployment.yaml"
                                        test -f "$MANIFEST" || {
                                            echo "ERROR: Deployment and manifest missing for $SERVICE_NAME"
                                            exit 1
                                        }
                                        kubectl apply -f "$MANIFEST" -n "$K8S_NAMESPACE"
                                    fi

                                    CONTAINERS=$(kubectl get "$DEPLOYMENT" \
                                        -n "$K8S_NAMESPACE" \
                                        -o jsonpath='{.spec.template.spec.containers[*].name}')

                                    case " $CONTAINERS " in
                                        *" $SERVICE_NAME "*) ;;
                                        *)
                                            echo "ERROR: Container $SERVICE_NAME not found in $DEPLOYMENT"
                                            exit 1
                                            ;;
                                    esac

                                    kubectl set image "$DEPLOYMENT" "$SERVICE_NAME=$IMAGE" -n "$K8S_NAMESPACE"

                                    if ! kubectl rollout status "$DEPLOYMENT" -n "$K8S_NAMESPACE" --timeout=5m; then
                                        kubectl rollout undo "$DEPLOYMENT" -n "$K8S_NAMESPACE" || true
                                        kubectl get pods -n "$K8S_NAMESPACE" -o wide || true
                                        kubectl describe "$DEPLOYMENT" -n "$K8S_NAMESPACE" || true
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

        stage('Verify Deployment') {
            when {
                expression { return !!env.BUILD_SERVICES?.trim() }
            }
            steps {
                sh '''
                    set -eu
                    for service in $(echo "$BUILD_SERVICES" | tr ',' ' '); do
                        kubectl rollout status "deployment/$service" -n "$K8S_NAMESPACE" --timeout=5m
                        kubectl get "deployment/$service" -n "$K8S_NAMESPACE" -o wide
                    done
                    kubectl get pods -n "$K8S_NAMESPACE" -o wide
                    kubectl get services -n "$K8S_NAMESPACE"
                '''
            }
        }

        stage('Clean Docker Cache') {
            steps {
                sh '''
                    docker system df || true
                    docker image prune -f
                    docker builder prune -f --filter 'until=168h'
                    docker system df || true
                '''
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully.'
            echo "Services: ${env.BUILD_SERVICES ?: 'none'}"
            echo "Commit: ${env.CURRENT_COMMIT ?: 'unknown'}"
        }
        failure {
            echo 'Pipeline failed. Check the first failed stage in Console Output.'
        }
        always {
            echo 'Pipeline finished.'
        }
    }
}
