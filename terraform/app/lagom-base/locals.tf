locals {
    service_account_name = "${var.app_name}-service-account"
    akka_cluster_name = "${var.app_name}-akka-cluster"
    akka_management_http_port = "akka-mgmt-http"
    postgres_config_name = "${var.app_name}-postgres-config"
    play_secret_name = "play-service-${var.app_name}-secret"
    config_dir = "/var/lib/lagom/config"
    config_file = "production.conf"
    kafka_service_env = "KAFKA_SERVICE_NAME"
    http_endpoint_name = "endpoint"
}

