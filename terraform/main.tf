terraform {
    required_version = ">= 1.0.0"
    required_providers {
        kubernetes = {
            source = "hashicorp/kubernetes"
            version = ">= 2.4.1"
        }
        helm = {
            source = "hashicorp/helm"
            version = "~> 2.2.0"
        }
    }
}

module "echo" {
    source = "./echo"
}

// Then the actual application and its dependencies (like postgres, kafka, etc.)
module "application" {
    source = "./app"
    namespace = var.application_namespace
    environment = var.environment
    config_context = var.kube_config


    postgres_config = {
        port = 5432
        username = "dev"
        db = "lagom"
    }
}

