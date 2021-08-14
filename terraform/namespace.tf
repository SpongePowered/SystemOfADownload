
resource "kubernetes_namespace" "namespace" {
    metadata {
        annotations = {
            name = local.namespace_name
        }

        name = local.namespace_name
        labels = {
            environment = "development"
        }
    }
}
