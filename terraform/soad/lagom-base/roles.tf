resource "kubernetes_service_account" "akka-service-account" {
    metadata {
        name = local.service_account_name
        namespace = var.namespace
    }
}

resource "kubernetes_role_binding" "akka-pod-cluster-binding" {
    metadata {
        name = local.akka_cluster_name
        namespace = var.namespace
        annotations = {
            "prometheus.io/monitor" = "true"
            environment = var.environment
        }
    }
    role_ref {
        api_group = "rbac.authorization.k8s.io"
        kind = "Role"
        name = var.cluster_member_role_name
    }
    subject {
        kind = "ServiceAccount"
        namespace = var.namespace
        name = local.service_account_name
    }
}
