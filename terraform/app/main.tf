module "system-of-a-download" {
    source = "./soad"

    environment = var.environment
    postgres_password = random_password.postgres_password.result
    postgres_service = {
        host = "module.postgres.service_host"
        port = "module.postgres.database_config.port"
        db = "module.postgres.database_config.db"
        username = " module.postgres.database_config.user"
        password_name = "application-postgres-password"
        password_key = "module.postgres.database_config.password_key"
    }
    lagom_services = {
        "auth" = {
            image_version = "latest"
        }
    }
}

resource "random_password" "postgres_password" {
    length = 32
    keepers = {
        namespace = var.namespace
    }
    min_lower = 16
    min_special = 6
    special = true
    upper = true
}

