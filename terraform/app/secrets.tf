resource "random_string" "auth-encryption" {
    length = 32
    special = false
    keepers = {
        namespace = var.namespace
    }
}

resource "random_string" "auth-signature" {
    length = 32
    special = false
    keepers = {
        namespace = var.namespace
    }
}
