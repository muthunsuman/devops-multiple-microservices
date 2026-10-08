1. Install Java 17, Maven, Docker, AWS CLI, Terraform, kubectl and Git on Jenkins agent.
2. Apply reviewed Terraform modules for VPC, ECR and EKS; configure remote state.
3. Configure Jenkins AWS role for ECR push and `gitops-write-token` credential if direct GitOps push is allowed.
4. Configure GitHub webhook and Jenkins Pipeline/Multibranch job.
5. Install Argo CD and update the repo URL in `gitops/argocd/application-*.yaml`.
6. Install AWS Load Balancer Controller for the ALB ingress.
7. Install kube-prometheus-stack and configure service discovery for all ten services.
8. Store SMTP password in a Kubernetes Secret and mount it at Alertmanager's configured path.
9. Test deployment, endpoint routing, alerts and rollback in dev before promotion.
