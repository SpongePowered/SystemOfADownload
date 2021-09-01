resource "kubernetes_service_account" "akka-cluster-instance" {
    metadata {
        name = "akka-cluster-${var.app_name}-service-account"
        namespace = var.namespace
        labels = {
            app = var.app_name
        }
    }
}

resource "kubernetes_role" "akka-cluster-reader-role" {
    metadata {
        name = "akka-cluster-${var.app_name}-role"
        namespace = var.namespace
        annotations = {
            app = var.app_name
        }
    }
    rule {
        api_groups = [
            ""]
        resources = [
            "pods"]
        verbs = [
            "get",
            "watch",
            "list"]
    }
}

resource "kubernetes_role_binding" "akka-cluster-binding" {
    metadata {
        name = "akka-cluster-${var.app_name}-binding"
        namespace = var.namespace
    }
    role_ref {
        api_group = "rbac.authorization.k8s.io"
        kind = "Role"
        name = kubernetes_role.akka-cluster-reader-role.metadata[0].name
    }
    subject {
        kind = "ServiceAccount"
        namespace = kubernetes_service_account.akka-cluster-instance.metadata[0].namespace
        name = kubernetes_service_account.akka-cluster-instance.metadata[0].name
    }
}
