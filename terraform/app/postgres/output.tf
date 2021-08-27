output "database_config" {
    value = var.database_config
}

output "name" {
    value = var.name
}

output "service_host" {
    value = "${kubernetes_service.systemofadownload_postgres.metadata[0].name}.${kubernetes_service.systemofadownload_postgres.metadata[0].namespace}"
}
