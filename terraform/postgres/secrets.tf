
resource "kubernetes_secret" "postgres_password" {
    metadata {
        name = var.database_config.password_name
        namespace = var.namespace
    }
    data = {
        (var.database_config.password_key) = var.password
        config = <<EOF
listen_addresses = '*'
max_connections = 500
EOF
    }
    type = "Opaque"
}
