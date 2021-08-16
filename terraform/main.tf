terraform {
    required_version = ">= 1.0.0"
    required_providers {
        kubernetes = {
            source = "hashicorp/kubernetes"
            version = ">= 2.0.0"
        }
        helm = {
            source = "hashicorp/helm"
            version = "~> 2.2.0"
        }
    }
}

module "postgres" {
    source = "./postgres"

    config_context = var.config_context
    db = "default"
    name = "systemofadownload-postgres"
    namespace = "lagom"
    password = random_password.postgres_password.result
    environment = local.environment
}

module "monitoring" {
    source = "./monitoring"
    target_namespace = "monitoring"
    environment = local.environment
}

