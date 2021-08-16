resource "kubernetes_namespace" "monitoring" {
    metadata {
        annotations = {
            name = var.target_namespace
        }
        labels = {
            purpose = "monitoring"
            environment = var.environment
        }
        name = var.target_namespace
    }
}
