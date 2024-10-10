terraform {
    required_version = ">= 1.0.0"
    required_providers {
        kubernetes = {
            source = "hashicorp/kubernetes"
            version = ">= 2.4.1"
        }
        helm = {
            source = "hashicorp/helm"
            version = "~> 2.16.0"
        }
        postgresql = {
            source = "cyrilgdn/postgresql"
            version = "1.21.0"
        }
        tls = {
            source = "hashicorp/tls"
            version = "4.0.6"
        }
    }
}

data "kubernetes_namespace" "application_namespace" {
    metadata {
        name = var.application_namespace
        annotations = {
            env = var.environment
        }
    }
}

resource "random_password" "postgres_password" {
    length = 32
    keepers = {
        namespace = var.application_namespace
    }
    min_lower = 16
    min_special = 6
    special = true
    upper = true
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
    namespace = var.application_namespace
    password = random_password.postgres_password.result
}
module "kafka" {
    source = "./kafka"
    namespace = var.application_namespace
    kafka_replicas = var.environment == "dev" ? 1 : 3
    zookeeper_replicas = var.environment == "dev" ? 1 : 3
}
// Then the actual application and its dependencies (like postgres, kafka, etc.)
module "application" {
    depends_on = [module.postgres, module.kafka]
    source = "./app"
    namespace = var.application_namespace
    environment = var.environment
    postgres_service = merge(module.postgres.database_config, {
        host = module.postgres.service_host
        username = module.postgres.database_config.user
    })
    postgres_password = random_password.postgres_password.result
}

