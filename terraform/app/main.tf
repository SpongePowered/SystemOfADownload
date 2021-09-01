module "system-of-a-download" {
    depends_on = [module.postgres]
    source = "./soad"

    environment = var.environment
    postgres_password = random_password.postgres_password.result
    postgres_service = {
        host = module.postgres.service_host
        port = module.postgres.database_config.port
        db = module.postgres.database_config.db
        username = module.postgres.database_config.user
        password_name = module.postgres.database_config.password_name
        password_key = module.postgres.database_config.password_key
    }
    lagom_services = {
        "auth" = {
            image_version = "latest"
        },
        artifacts = {
            image_version = "latest"
        },
        artifacts_query = {
            image_version = "latest"
        }
    }
}

module "postgres" {
    depends_on = [random_password.postgres_password]
    source = "./postgres"

    database_config = {
        db = "default"
        user = "lagom"
        port = 5432
        password_name = "postgres-password"
        password_key = "pgpass"
    }
    environment = var.environment
    name = "lagom-postgres"
    namespace = var.namespace
    password = random_password.postgres_password.result
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

