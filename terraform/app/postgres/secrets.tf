
resource "kubernetes_secret" "postgres_password" {
    metadata {
        name = var.database_config.password_name
        namespace = var.namespace
    }
    data = {
        (var.database_config.password_key) = var.password
    }
    type = "Opaque"
}
