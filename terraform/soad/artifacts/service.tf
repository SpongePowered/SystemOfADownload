module "lagom-base" {
    source = "./../lagom-base"

    app_name = "artifacts"
    environment = var.environment
    app_image = ""
    app_version = ""
    namespace = var.namespace
    postgres_host = var.postgres_host
    postgres_password = var.postgres_password
    postgres_password_name = var.postgres_port
    postgres_username = var.postgres_username
    cluster_member_role_name = var.cluster_member_role
}

output "play_secret" {
    value = module.lagom-base.play-secret
}
