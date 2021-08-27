variable "app_name" {
    type = string
    description = "The name of this lagom service application"
}

variable "app_image" {
    type = string
    description = "The image to use to deploy, including version"
}

variable "app_version" {
    type = string
    description = "The image version"
}
variable "environment" {
    type = string
}

variable "namespace" {
    type = string
}

variable "replica-count" {
    type = number
    default = 3
    description = "The count of replicas to make"
}

variable "play_secret_key" {
    type = string
    sensitive = true
}

variable "extra_config" {
    type = string
}

variable "extra_envs" {
    type = map(object({
        value = string
    }))
    default = {}
}
variable "extra_secret_envs" {
    type = map(object({
        name = string
        key = string
    }))
    default = {}
}

variable "classpath" {
    type = string
}
