pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds(); timeout(time: 60, unit: 'MINUTES') }
  environment {
    ///AWS_REGION = 'ap-south-1'
    //AWS_ACCOUNT_ID = '111122223333'
    //ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    //IMAGE_TAG = "${BUILD_NUMBER}-${GIT_COMMIT}"
    //GITOPS_FILE = 'gitops/environments/dev/kustomization.yaml'
  }
  stages {
    stage('Checkout') { steps { checkout scm } }
    stage('Detect changes') {
      steps {
        script {
          def base = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: sh(script: 'git rev-parse HEAD~1', returnStdout: true).trim()
          def output = sh(script: "GIT_BASE='${base}' GIT_HEAD=HEAD ./scripts/detect-changes.sh", returnStdout: true).trim()
          env.CHANGED_SERVICES = output.readLines().findAll { it?.trim() }.join(',')
          echo "Changed services: ${env.CHANGED_SERVICES ?: '(none)'}"
        }
      }
    }
    stage('Maven test') {
      when { expression { return env.CHANGED_SERVICES?.trim() } }
      steps {
        script {
          def jobs = [:]
          env.CHANGED_SERVICES.split(',').each { svc ->
            jobs[svc] = { dir("services/${svc}") { sh 'mvn -B clean verify' } }
          }
          parallel jobs
        }
      }
    }
    stage('Docker build and ECR push') {
      when { expression { return env.CHANGED_SERVICES?.trim() } }
      steps {
        sh 'aws sts get-caller-identity'
        sh 'aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"'
        script {
          def jobs = [:]
          env.CHANGED_SERVICES.split(',').each { svc ->
            jobs[svc] = {
              sh("set -eu; aws ecr describe-repositories --repository-names '${svc}' --region '${AWS_REGION}' >/dev/null; docker build --progress=plain -f docker/Dockerfile -t '${ECR_REGISTRY}/${svc}:${IMAGE_TAG}' 'services/${svc}'; docker push '${ECR_REGISTRY}/${svc}:${IMAGE_TAG}'")
            }
          }
          parallel jobs
        }
      }
    }
    stage('Update GitOps image tags') {
      when { expression { return env.CHANGED_SERVICES?.trim() } }
      steps {
        withCredentials([usernamePassword(credentialsId: 'gitops-write-token', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
          sh '''
            set -eu
            git config user.name "jenkins-gitops-bot"
            git config user.email "jenkins-gitops-bot@example.com"
            for svc in $(echo "$CHANGED_SERVICES" | tr ',' ' '); do
              awk -v svc="$svc" -v tag="$IMAGE_TAG" '
                $0 ~ "name: .*amazonaws.com/" svc "$" { found=1 }
                found && $1 == "newTag:" { sub(/newTag:.*/, "newTag: \"" tag "\""); found=0 }
                { print }
              ' "$GITOPS_FILE" > "$GITOPS_FILE.tmp"
              mv "$GITOPS_FILE.tmp" "$GITOPS_FILE"
            done
            git add "$GITOPS_FILE"
            if ! git diff --cached --quiet; then
              git commit -m "Deploy ${CHANGED_SERVICES} image ${IMAGE_TAG}"
              git remote set-url origin "https://${GIT_USER}:${GIT_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
              git push origin HEAD:main
            fi
          '''
        }
      }
    }
    stage('Verify GitOps build') {
      steps {
        sh 'git diff --check'
        echo 'Argo CD should watch gitops/environments/dev and reconcile the updated tags.'
      }
    }
  }
  post {
    failure { echo 'Pipeline failed; inspect the first failing stage and logs.' }
    always { echo "Build ${BUILD_NUMBER} finished." }
  }
}
