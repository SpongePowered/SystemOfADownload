variable "database_config" {
    type = object({
        db = string
        user = string
        port = number
        password_name = string
        password_key = string
    })
    validation {
        condition = can(regex("^[\\w_]+$", var.database_config.db))
        error_message = "Postgres database can only be alpha-numeric characters with _ as word separators."
    }
    validation {
        condition = min(65525, var.database_config.port) == var.database_config.port && max(1, var.database_config.port) != 1
        error_message = "Postgres port must be within standard range."
    }
    validation {
        condition = can(regex("^[\\w_]+$", var.database_config.user))
        error_message = "The db user must be defined."
    }
}

variable "namespace" {
    type = string
}

variable "password" {
    type = string
    sensitive = true
}

variable "name" {
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
