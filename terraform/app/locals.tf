locals {

    encryption_key = sha512(random_string.auth-encryption.result)
    signature_key = sha512(random_string.auth-signature.result)

    soad_app_configs = {
        "artifacts" = {
            service_name = "artifacts-server"
            lagom = {
                service_name = "artifacts"
            }
            image = {
                replicas = 1
                image_version = "latest"
                image_name = "spongepowered/systemofadownload-artifact-impl"
            }
            extra_env = local.default_database_envs
            extra_secret_envs = local.default_secret_based_envs
            kafka_topics = {
                "artifact-updates" = {
                    topic = "group-activity"
                }
            }
        }
        "artifacts_query" = {
            service_name = "artifacts-query-server"
            lagom = {
                service_name = "artifact-query"
            }
            image = {
                replicas = var.environment == "dev" ? 1 : 2
                image_version = "latest"
                image_name = "spongepowered/systemofadownload-artifact-query-impl"
            }
            extra_env = local.default_database_envs
            extra_secret_envs = local.default_secret_based_envs
        }
        "auth" = {
            service_name = "auth-server"
            lagom = {
                service_name = "auth"
            }
            image = {
                replicas = 1
                image_version = "latest"
                image_name = "spongepowered/systemofadownload-auth-impl"
            }
            extra_config =  <<-EOF
            systemofadownload.auth {
                use-dummy-ldap = true
                expiration = "10d"
            }
            EOF
        }
        "versions" = {
            service_name = "versions-server"
            replicas = var.environment == "dev" ? 1 : 3
            image = {
                version = "latest"
                name = "spongepowered/systemofadownload-versions-impl"
            }
            extra_env = local.default_database_envs
            extra_secret_envs = local.default_secret_based_envs
            kafka_topics = {
                "artifact-versions" = {
                    topic = "artifact-update"
                }
            }
        }
        "versions_query" = {
            service_name = "versions-query-server"
            replicas = var.environment == "dev" ? 1 : 3
            image = {
                version = "latest"
                name = "spongepowered/systemofadownload-versions-query-impl"
            }
            extra_env = local.default_database_envs
            extra_secret_env = local.default_secret_based_envs
        }
        "synchronizer" = {
            service_name = "version-synchronizer"
            replicas = 1
            image = {
                version = "latest"
                name = "spongepowered/systemofadownload-version-synchronizer"
            }
            extra_env = local.default_database_envs
            extra_secret_env = local.default_secret_based_envs
            extra_config =  <<-EOF
            akka {
              remote {
                artery {
                  canonical.port = 28825
                  canonical.hostname = "0.0.0.0"
                }
              }
            }
            EOF
            extra_ports = [{
                port = 28825
                name = "artery"
                protocol = "TCP"
            }]
        }
        "gateway" = {
            service_name = "soad-gateway"
            replicas = var.environment == "dev" ? 1 : 3
            image = {
                version = "latest"
                name = "spongepowered/systemofadownload-gateway-impl"
            }
        }
    }

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


}
