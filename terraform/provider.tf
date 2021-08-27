provider "kubernetes" {
    config_path = pathexpand(var.kube_config)
    experiments {
        manifest_resource = true
    }
}

provider "helm" {
    kubernetes {
        config_path = pathexpand(var.kube_config)
    }
}
