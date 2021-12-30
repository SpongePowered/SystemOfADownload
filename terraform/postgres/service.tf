resource "kubernetes_deployment" "systemofadownload-postgres" {
    depends_on = [
        kubernetes_secret.postgres_password,
        kubernetes_persistent_volume_claim.postgres-data-claim,
        kubernetes_persistent_volume.postgres-data
    ]
    metadata {
        namespace = var.namespace
        name = var.name
        labels = {
            "prometheus.io/scrape" = "true"
            environment = var.environment
        }
    }
    spec {
        replicas = 1
        selector {
            match_labels = {
                app = var.name
            }
        }
        template {
            metadata {
                namespace = var.namespace
                labels = {
                    app = var.name
                }
            }
            spec {
                container {
                    name = var.name
                    image = local.image

                    args = ["-c", "max_connections=500", "-c", "shared_buffers=1024MB"]
                    env {
                        name = "POSTGRES_PASSWORD"
                        value_from {
                            secret_key_ref {
                                name = kubernetes_secret.postgres_password.metadata.0.name
                                key = var.database_config.password_key
                            }
                        }
                    }
                    env {
                        name = "POSTGRES_USER"
                        value = var.database_config.user
                    }
                    env {
                        name = "POSTGRES_DB"
                        value = var.database_config.db
                    }
                    volume_mount {
                        mount_path = "/var/lib/postgresql/data"
                        name = var.storage_name
                    }
                    resources {
                        requests = {
                            cpu = local.cpu
                            memory = local.memory
                        }
                        limits = {
                            cpu = local.cpu_max
                            memory = local.memory_max
                        }
                    }
                    port {
                        container_port = var.database_config.port
                        protocol = "TCP"
                        name = "postgres"
                    }
                    liveness_probe {
                        exec {
                            command = [
                                "/bin/sh",
                                "-c",
                                "exec pg_isready -U \"${var.database_config.user}\" -d ${var.database_config.db} -h 127.0.0.1 -p ${var.database_config.port}"
                            ]
                        }
                        failure_threshold = 6
                        initial_delay_seconds = 30
                        period_seconds = 10
                        success_threshold = 1
                        timeout_seconds = 5
                    }
                    readiness_probe {
                        exec {
                            command = [
                                "/bin/sh",
                                "-c",
                                "exec pg_isready -U \"${var.database_config.user}\" -d ${var.database_config.db} -h 127.0.0.1 -p ${var.database_config.port}"
                            ]
                        }
                        failure_threshold = 6
                        initial_delay_seconds = 30
                        period_seconds = 10
                        success_threshold = 1
                        timeout_seconds = 5
                    }
                }
                volume {
                    name = var.storage_name
                    persistent_volume_claim {
                        claim_name = var.storage_claim
                    }
                }
                volume {
                    name = "postgres-config"
                    secret {
                        secret_name = kubernetes_secret.postgres_password.metadata[0].name
                        items {
                            path = "postgresql.conf"
                            key = "config"
                        }
                    }
                }
            }
        }
    }
}

resource "kubernetes_service" "systemofadownload_postgres" {
    metadata {
        name = var.name
        namespace = var.namespace
    }
    spec {
        port {
            protocol = "TCP"
            port = var.database_config.port
            target_port = var.database_config.port
            name = "postgres"
        }
        selector = {
            app = var.name
        }
    }
}
