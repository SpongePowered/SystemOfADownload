resource "kubernetes_service" "lagom-services" {
    metadata {
        name = var.app_name
        labels = {
            app = var.app_name
            "prometheus.io/scrape" = false
            environment = var.environment
        }
        namespace = var.namespace
    }
    spec {
        port {
            name = "http"
            port = 80
            target_port = "9000"
        }
        selector = {
            app = var.app_name
            environment = var.environment
        }
        type = "LoadBalancer"
    }
}

resource "kubernetes_deployment" "lagom-instances" {
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
        selector {
            match_labels = {
                app = var.app_name
                environment = var.environment
            }
        }
        strategy {
            rolling_update {
                max_surge = "1"
                max_unavailable = "0"
            }
            type = "RollingUpdate"
        }
        replicas = var.replica-count
        template {
            metadata {
                labels = {
                    app = var.app_name
                    environment = var.environment
                    "prometheus.io/scrape" = true
                }
                name = var.app_name
            }
            spec {
                service_account_name = kubernetes_service_account.akka-cluster-instance.metadata[0].name
                container {
                    name = var.app_name
                    image = "${var.app_image}:${var.app_version}"
                    image_pull_policy = "Always"

                    resources {
                        requests = {
                            cpu = "100m"
                            memory = "200Mi"
                        }
                        limits = {
                            cpu = "750m"
                            memory = "1Gi"
                        }
                    }
                    dynamic "env" {
                        for_each = var.extra_envs
                        content {
                            name = env.key
                            value = env.value.value
                        }
                    }
                    dynamic "env" {
                        for_each = var.extra_secret_envs
                        content {
                            name = env.key
                            value_from {
                                secret_key_ref {
                                    name = env.value.name
                                    key = env.value.key
                                }
                            }
                        }
                    }
                    env {
                        name = "AKKA_CLUSTER_BOOTSTRAP_SERVICE_NAME"
                        value = var.app_name
                    }

                    env {
                        name = "JAVA_OPTS"
                        value = "-XX:+UnlockExperimentalVMOptions -Dconfig.file=${local.config_dir}/${local.config_file} ${var.extra_java_opts}"
                    }
                    env {
                        name = "APPLICATION_SECRET"
                        value_from {
                            secret_key_ref {
                                name = kubernetes_secret.play_secret.metadata[0].name
                                key = var.play_secret_key
                            }
                        }
                    }
                    env {
                        name = local.kafka_service_env
                        value = "_tcp-clients._tcp.${var.kafka_config.service_name}.${var.namespace}"
                    }

                    liveness_probe {
                        http_get {
                            path = "health/alive"
                            port = "management"
                        }
                        initial_delay_seconds = 20
                        failure_threshold = 1
                        success_threshold = 1
                        period_seconds = 5
                    }
                    readiness_probe {
                        http_get {
                            port = "management"
                            path = "health/ready"
                        }
                        initial_delay_seconds = 20
                        period_seconds = 5
                        success_threshold = 1
                        failure_threshold = 3
                    }
                    port {
                        name = "remoting"
                        container_port = 2552
                        protocol = "TCP"
                    }
                    port {
                        name = "management"
                        container_port = 8558
                        protocol = "TCP"
                    }
                    port {
                        name = "endpoint"
                        container_port = 9000
                        protocol = "TCP"
                    }
                    port {
                        name = "debug"
                        container_port = 5005
                        protocol = "TCP"
                    }

                    volume_mount {
                        mount_path = local.config_dir
                        name = "application-${var.app_name}-config"
                        read_only = true
                    }
                }
                volume {
                    name = "application-${var.app_name}-config"
                    secret {
                        secret_name = kubernetes_secret.lagom_application_config.metadata[0].name
                    }
                }
            }
        }
    }
}

