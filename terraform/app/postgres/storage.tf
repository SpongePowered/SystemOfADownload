resource "kubernetes_persistent_volume" "postgres-data" {
    metadata {
        name = "postgres-data-volume"
        labels = {
            type: "local"
            app: var.name
        }
    }
    spec {
        access_modes = [ "ReadWriteOnce" ]
        capacity = {
            storage = "10Gi"
        }
        persistent_volume_reclaim_policy = "Retain"
        storage_class_name = "standard"
        persistent_volume_source {
            host_path {
                path = "/data"
            }
        }
    }
}

resource "kubernetes_persistent_volume_claim" "postgres-data-claim" {
    metadata {
        name = var.storage_claim
        namespace = var.namespace
        labels = {
            app: var.name
        }
    }

    spec {
        access_modes = [ "ReadWriteOnce" ]
        storage_class_name = "standard"
        resources {
            requests = {
                storage = "10Gi"
            }
        }
    }
}
