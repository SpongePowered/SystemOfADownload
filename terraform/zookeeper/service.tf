resource "kubernetes_stateful_set" "systemofadownload_zookeeper" {
    metadata {
        name = var.name
        namespace = var.namespace
    }
    spec {
        service_name = ""
        selector {}
        template {
            metadata {}
        }
    }
}
