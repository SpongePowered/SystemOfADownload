
resource "kubernetes_secret" "postgres_password" {
    metadata {
        name = var.password_name
        namespace = var.namespace
    }
    data = {
        (var.password_key) = var.password
    }
    type = "Opaque"
}
