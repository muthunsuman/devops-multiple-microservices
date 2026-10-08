# Spring Boot DevOps Platform

A monorepo starter with ten Spring Boot REST services, Maven tests, Docker, Jenkins, ECR, EKS/Kubernetes, Argo CD GitOps, Prometheus alert rules, Alertmanager email routing, Grafana guidance, Terraform modules, scripts, and operational docs.

## Local run
```bash
cd services/user-service
mvn spring-boot:run
curl http://localhost:8080/api/users
curl http://localhost:8080/actuator/health
```
All services default to port 8080. Run one at a time locally, or override `SERVER_PORT`.

## Test all services
```bash
for d in services/*; do (cd "$d" && mvn -B clean verify) || exit 1; done
```

## Before deployment
Replace AWS account ID `111122223333`, GitHub org `YOUR_ORG`, namespace/domain/email examples. Create ECR repositories and configure Jenkins AWS role, GitHub webhook, GitOps write credential, Argo CD repository access, EKS, and SMTP Secret. Review Terraform plan before applying.

The REST services have working in-memory create/list/get/delete endpoints for demonstration. They do not provide durable storage, production authentication, real payment processing, or production-ready business logic. Terraform EKS/VPC/IAM files are starter modules and must be reviewed and wired together before deployment.
