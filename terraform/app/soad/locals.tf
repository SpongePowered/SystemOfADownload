locals {
    soad_extra_environment_variables = {
        "artifacts" = local.default_database_envs
        "artifacts_query" = local.default_database_envs
        "auth" = {}
        "synchronizer" = {}
        "versions" = local.default_database_envs
        "versions_query" = local.default_database_envs
    }
    service_definitions = merge(var.lagom_services, var.soad_images)

    default_database_envs = {
        "POSTGRES_URL" = {
            value = var.postgres_service.host
        }
        "POSTGRES_USERNAME" = {
            value = var.postgres_service.username
        }
        "POSTGRES_DB" = {
            value = var.postgres_service.db
        }
        "POSTGRES_HOST" = {
            value = var.postgres_service.host
        }
    }
    default_secret_based_envs = {
        "POSTGRES_PASSWORD" = {
            name = var.postgres_service.password_name
            key = var.postgres_service.password_key
        }
    }
    secret_based_envs = {
        "artifacts" = local.default_secret_based_envs
        "artifacts_query" = local.default_secret_based_envs
        "auth" = {}
        "synchronizer" = {}
        "versions" = local.default_secret_based_envs
        "versions_query" = local.default_secret_based_envs
    }
    extra_configs = {
        "artifacts" = ""
        "artifacts_query" = ""
        "auth" = <<-EOF
        systemofadownload.auth {
            use-dummy-ldap = true
            expiration = "10d"
        }
        EOF
        "synchronizer" = ""
        "versions" = ""
        "versions_query" = ""
    }

    additional_java_opts = {
        "artifacts" = ""
        "artifacts_query" = ""
        "auth" = ""
        "synchronizer" = ""
        "versions" = ""
        "versions_query" = ""
    }

}
