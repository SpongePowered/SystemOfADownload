locals {
    service_account_name = "${var.app_name}-service-account"
    akka_cluster_name = "${var.app_name}-akka-cluster"
    akka_management_http_port = "akka-mgmt-http"
    postgres_config_name = "${var.app_name}-postgres-config"
    play_secret_name = "play-service-${var.app_name}-secret"
    config_value = "/etc/lagom/production.conf"
}

