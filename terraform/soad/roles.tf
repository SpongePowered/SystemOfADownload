# This is the core akka-role group
resource "kubernetes_role" "akka-pod-cluster-reader" {
    metadata {
        name = local.akka_cluster_member
        namespace = var.namespace
        annotations = {
            "prometheus.io/monitor" = "true"
            environment = var.environment
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
