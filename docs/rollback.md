1. Pause promotion and identify last known-good immutable image tag.
2. Revert the GitOps image tag through approved review.
3. Let Argo CD reconcile.
4. Verify rollout status, ready replicas, service endpoints, gateway request and external health check.
5. Check DB migration compatibility; application rollback does not reverse schema migrations.
6. Confirm alerts resolve, document root cause, and add regression tests.
