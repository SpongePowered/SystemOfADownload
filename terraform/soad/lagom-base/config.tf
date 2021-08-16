resource "kubernetes_config_map" "lagom-postgres-config" {
    metadata {
        name = local.postgres_config_name
    }
    data = {
        db = var.postgres_db
        username = var.postgres_username
        port = var.postgres_port
    }
}
