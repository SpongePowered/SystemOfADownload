resource "kubernetes_secret" "play_secret" {
    metadata {
        name = "play-service-${var.app_name}-secret"

    }
}

resource "random_password" "play_secret" {
    length = 64
    special = true
}

resource "kubernetes_secret" "postgres_password" {
    metadata {
        name = var.postgres_password_name
        namespace = var.namespace
    }
    data = {
        (var.password_key) = var.postgres_password
    }
    type = "Opaque"
}
