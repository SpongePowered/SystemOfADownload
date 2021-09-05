variable "namespace" {
    type = string
    default = "lagom"
}

variable "environment" {
    type = string
}
variable "postgres_service" {
    type = object({
        host = string,
        port = string,
        db = string,
        username = string
        password_name = string
        password_key = string
    })
}

variable "postgres_password" {
    type = string
    sensitive = true
}

variable "jvm_args" {
    type = string
    default = "java -XX:+UnlockExperimentalVMOptions --illegal-access=permit -cp '/maven/*' -Dplay.http.secret.key=$PLAY_APPLICATION_SECRET -Dconfig.resource=/etc/production.conf play.core.server.ProdServerStart"
}

variable "lagom_services" {
    description = "The map of project names to their module configurations"
    type = map(object({
        image_version = string,
    }))
    default = {
        artifacts = {
            image_version = "latest"
        },
        artifacts_query = {
            image_version = "latest"
        },
        auth = {
            image_version = "latest"
        },
        synchronizer = {
            image_version = "latest"
        },
        versions = {
            image_version = "latest"
        },
        versions_query = {
            image_version = "latest"
        },
        gateway = {
            image_version = "latest"
        }
    }
}

variable "soad_images" {
    type = map(object({
        service_name = string
        image_name = string
        image_version = string
        replicas = number
    }))
    default = {

    }
}
