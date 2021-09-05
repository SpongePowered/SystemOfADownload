
resource "kubernetes_job" "soad-liquibase-migration" {
    metadata {
        name = "soad-lagom-migration"
        namespace = var.namespace
    }
    spec {
        template {
            metadata {
                name = "soad-liquibase-migrator"
                annotations = {
                    app = "liquibase"
                    component = "systemofadownload"
                }
            }
            spec {
                container {
                    name = "soad-liquibase"
                    image = "spongepowered/systemofadownload-liquibase:latest"

                    env {
                        name = "URL"
                        value = var.postgres_service.host
                    }
                    env {
                        name = "USERNAME"
                        value = var.postgres_service.username
                    }
                    env {
                        name = "PASSWORD"
                        value_from {
                            secret_key_ref {
                                name = var.postgres_service.password_name
                                key = var.postgres_service.password_key
                            }
                        }
                    }
                    resources {
                        limits = {
                            cpu = "500m"
                            memory = "256Mi"
                        }
                    }
                }

            }
        }
        backoff_limit = 4
        ttl_seconds_after_finished = 15
    }
    wait_for_completion = true
}
