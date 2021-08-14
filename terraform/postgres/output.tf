
output "port" {
    value = var.port
}

output "user" {
    value = var.user
}

output "db" {
    value = var.db
}

output "name" {
    value = var.name
}

output "secret_name" {
    value = kubernetes_secret.postgres_password.metadata.0.name
}
