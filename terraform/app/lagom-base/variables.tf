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
    default = "play_secret_key"
}

variable "extra_config" {
    type = string
    default = ""
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

variable "extra_java_opts" {
    type = string
    default = ""
}

variable "kafka_config" {
    type = object({
        service_name = string
    })
    default = {
        service_name = "lagom-kafka-kafka-brokers"
    }
}

variable "extra_ports" {
    type = list(object({
        name = string
        port = number
        protocol = string
    }))
    default = []
}

variable "kafka_topics" {
    type = map(object({
        topic = string
    }))
    default = {}
}

variable "encryption_key" {
    type = string
    sensitive = true
}

variable "signature_key" {
    type = string
    sensitive = true
}
