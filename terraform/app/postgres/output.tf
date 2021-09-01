output "database_config" {
    value = var.database_config
}

output "name" {
    value = var.name
}

output "service_host" {
    value = "jdbc:postgresql://${kubernetes_service.systemofadownload_postgres.spec[0].cluster_ip}:${var.database_config.port}/${var.database_config.db}"
}
