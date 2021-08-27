variable "environment" {
    type = string
}

variable "application_namespace" {
    type = string
    description = "The namespace for SystemOfADownload to be deployed to."
}

variable "kube_config" {
    type = string
    default = "~/.kube/config"
}

variable "monitoring_namespace" {
    type = string
    default = "monitoring"
}
