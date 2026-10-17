import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.DockerCommandStep
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.pipelines.*
import jetbrains.buildServer.configs.kotlin.triggers.gitHubChecks
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2026.1"

project {

    vcsRoot(HttpsGithubComMuthunsumanDevopsMultipleMicroservicesRefsHeadsMain1)

    buildType(BuildAndDeploy)

    pipeline(DevopsMultipleMicroservices)
}

object BuildAndDeploy : BuildType({
    name = "Build and Deploy"

    vcs {
        root(HttpsGithubComMuthunsumanDevopsMultipleMicroservicesRefsHeadsMain1)
    }

    steps {
        maven {
            name = "Maven"
            id = "Maven"
            goals = "clean package"
            pomLocation = "services/api-gateway/pom.xml"
        }
        maven {
            name = "mvn test"
            id = "mvn_test"
            goals = "test"
            pomLocation = "services/api-gateway/pom.xml"
        }
        dockerCommand {
            name = "docker image build"
            id = "docker_image_build"
            commandType = build {
                source = file {
                    path = "services/api-gateway/Dockerfile"
                }
                contextDir = "services/api-gateway"
                platform = DockerCommandStep.ImagePlatform.Linux
                namesAndTags = "028610956643.dkr.ecr.ap-southeast-2.amazonaws.com/devopsmultiplemicroservice"
                commandArgs = "--pull"
            }
        }
        script {
            name = "login AWS ECR for image push"
            id = "docker_image_build_1"
            scriptContent = """
                set -eu
                
                aws --version
                
                aws ecr get-login-password --region ap-southeast-2 \
                  | docker login \
                      --username AWS \
                      --password-stdin 028610956643.dkr.ecr.ap-southeast-2.amazonaws.com
            """.trimIndent()
        }
        dockerCommand {
            name = "docker imahe push"
            id = "docker_imahe_push"
            commandType = push {
                namesAndTags = "028610956643.dkr.ecr.ap-southeast-2.amazonaws.com/devopsmultiplemicroservice"
                removeImageAfterPush = false
            }
        }
        script {
            name = "Login to Amazon ECR."
            id = "Login_to_Amazon_ECR"
            enabled = false
            scriptContent = """
                set -eu
                
                aws --version
                
                aws ecr get-login-password --region ap-southeast-2 \
                  | docker login \
                      --username AWS \
                      --password-stdin 028610956643.dkr.ecr.ap-southeast-2.amazonaws.com
            """.trimIndent()
        }
    }

    triggers {
        gitHubChecks {
            disableLinksToTeamcity = true
            param("prOnly", "true")
        }
    }

    features {
        notifications {
            notifierSettings = emailNotifier {
                email = "muthunsuman2025@mail.com"
            }
            buildFailedToStart = true
            buildFailed = true
            firstFailureAfterSuccess = true
            newBuildProblemOccurred = true
            buildFinishedSuccessfully = true
            firstSuccessAfterFailure = true
            firstBuildErrorOccurs = true
            buildProbablyHanging = true
            queuedBuildRequiresApproval = true
        }
    }
})

object HttpsGithubComMuthunsumanDevopsMultipleMicroservicesRefsHeadsMain1 : GitVcsRoot({
    name = "https://github.com/muthunsuman/devops-multiple-microservices#refs/heads/main (1)"
    url = "https://github.com/muthunsuman/devops-multiple-microservices"
    branch = "refs/heads/main"
    branchSpec = "refs/heads/*"
    authMethod = password {
        userName = "muthunsuman"
        password = "credentialsJSON:ca3f7755-cd56-463b-ac63-32048601e33a"
    }
})


object DevopsMultipleMicroservices : Pipeline({
    name = "Devops Multiple Microservices"

    repositories {
        repository(DslContext.settingsRoot)
    }

    triggers {
        vcs {
        }
    }

    job(DevopsMultipleMicroservices_Job1)
})

object DevopsMultipleMicroservices_Job1 : Job({
    id("Job1")
    name = "Job 1"
})
