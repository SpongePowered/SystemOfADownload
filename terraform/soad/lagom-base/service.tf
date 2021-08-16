resource "kubernetes_deployment" "lagom-service" {
    metadata {
        labels = {
            app = var.app_name
            "prometheus.io/scrape" = "true"
            environment = var.environment
        }
        name = var.app_name
        namespace = var.namespace
    }
    spec {
        replicas = var.replica-count
        selector {
            match_labels = {
                app = var.app_name
            }
        }
        strategy {
            rolling_update {
                max_surge = "1"
                max_unavailable = "0"
            }
            type = "RollingUpdte"
        }
        template {
            metadata {
                labels = {
                    app = var.app_name
                    "prometheus.io/scrape" = "true"
                    environment = var.environment
                }
                name = var.app_name
                namespace = var.namespace
            }
            spec {
                # Enables the akka-cluster permissions required to form a cluster of pods
                service_account_name = local.service_account_name
                container {
                    name = var.app_name
                    image = "${var.app_image}:${var.app_version}"
                    liveness_probe {
                        http_get {
                            path = "/alive"
                            port = "management"
                        }
                    }
                    readiness_probe {
                        http_get {
                            path = "/ready"
                            port = "management"
                        }
                    }
                    # akka-management bootstrap
                    port {
                        container_port = 9000
                        name = "http"
                    }
                    port {
                        container_port = 2552
                        name = "akka-remote"
                    }
                    port {
                        container_port = 8558
                        name = local.akka_management_http_port
                        protocol = "TCP"
                    }
                    # environment variables
                    env {
                        name = "AKKA_CLUSTER_BOOSTRAP_SERVICE_NAME"
                        value = kubernetes_role_binding.akka-pod-cluster-binding.subject[0].name
                    }
                    env {
                        name = "POSTGRES_URL"
                        value = "jdbc:postgresql://${var.postgres_host}:${var.postgres_port}/${var.postgres_db}"
                    }
                    env {
                        name = "POSTGRES_PASSWORD"
                        value_from {
                            secret_key_ref {
                                name = kubernetes_secret.postgres_password.metadata.0.name
                                key = var.postgres_password_key
                            }
                        }
                    }
                    env_from {
                        config_map_ref {
                            name = local.postgres_config_name
                        }
                    }
                    env {
                        name = "POSTGRES_USERNAME"
                        value_from {
                            config_map_key_ref {
                                name = kubernetes_config_map.lagom-postgres-config.metadata[0].name
                                key = kubernetes_config_map.lagom-postgres-config.data.username
                            }
                        }
                    }
                    env {
                        name = "POSTGRES_DB"
                        value = var.postgres_db
                    }
                    env {
                        name = "POSTGRES_JOURNAL_SCHEMA"
                        value = var.journal_schema
                    }
                }
            }
        }
    }
}
