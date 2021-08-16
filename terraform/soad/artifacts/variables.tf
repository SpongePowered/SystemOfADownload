variable "environment" {
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
variable "namespace" {
    type = string
    default = "lagom"
}

variable "cluster_member_role" {
    type = string
}
