variable "postgres_database" {
    type = string
    description = "The database to set up with Postgres"
    default = "default"
}

variable "namespace" {
    type = string
}

variable "port" {
    type = number
    default = 5432
}

variable "user" {
    type = string
    default = "admin"
}

variable "db" {
    type = string
}

variable "password_name" {
    type = string
    default = "postgres-password"
}

variable "password_key" {
    type = string
    default = "password"
}

variable "password" {
    type = string
    sensitive = true
}

variable "name" {
    type = string
}

variable "config_context" {
    type = string
}

variable "storage_name" {
    type = string
    default = "postgres-storage"
}

variable "storage_claim" {
    type = string
    default = "pg-storage-claim"
}

variable "environment" {
    type = string
}
