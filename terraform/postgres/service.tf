resource "kubernetes_deployment" "systemofadownload-postgres" {
    metadata {
        namespace = var.namespace
        name = var.name
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
                    env {
                        name = "POSTGRES_PASSWORD"
                        value_from {
                            secret_key_ref {
                                name = kubernetes_secret.postgres_password.metadata.0.name
                                key = var.password_key
                            }
                        }
                    }
                    env {
                        name = "POSTGRES_USER"
                        value = var.user
                    }
                    env {
                        name = "POSTGRES_DB"
                        value = var.db
                    }
                    volume_mount {
                        mount_path = "/var/lib/pgsql/data"
                        name = var.storage_name
                    }
                    resources {
                        requests = {
                            cpu = local.cpu
                            memory = local.memory
                        }
                    }
                    liveness_probe {
                        exec {
                            command = [
                                "/bin/sh",
                                "-c",
                                "exec pg_isready -U \"${var.user}\" -d ${var.db} -h 127.0.0.1 -p ${local.port}"
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
                                "exec pg_isready -U \"${var.user}\" -d ${var.db} -h 127.0.0.1 -p ${local.port}"
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
            port = var.port
            target_port = local.port
            name = "postgres-port"
        }
        selector = {
            namespace = var.namespace
            app = var.name
        }
    }
}
