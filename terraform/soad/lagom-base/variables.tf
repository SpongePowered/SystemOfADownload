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
    default = "development"
}

variable "namespace" {
    type = string
}

variable "replica-count" {
    type = number
    default = 3
    description = "The count of replicas to make"
}
variable "postgres_password_key" {
    type = string
    default = "password"
}

variable "postgres_password_name" {
    type = string
}
variable "postgres_password" {
    type = string
    sensitive = true
}
variable "postgres_username" {
    type = string
}
variable "postgres_db" {
    type = string
    default = "default"
}
variable "postgres_host" {
    type = string
}
variable "postgres_port" {
    type = number
    default = 5432
}
variable "journal_schema" {
    type = string
    default = "public"
}

variable "cluster_member_role_name" {
    type = string
}
