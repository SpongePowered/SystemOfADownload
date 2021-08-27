variable "namespace" {
    type = string
}

variable "environment" {
    type = string
}

variable "config_context" {
    type = string
}

variable "postgres_config" {
    type = object({
        port = string,
        db = string,
        username = string
    })
    sensitive = true
}
