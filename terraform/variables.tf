
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

variable "namespace" {
    type    = string
    default = "lagom"
}

variable "config_context" {
    type    = string
    default = "minikube"
}

variable "kube_config" {
    type = string
    default = "~/.kube/config"
}
