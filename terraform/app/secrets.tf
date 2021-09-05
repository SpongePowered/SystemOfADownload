resource "random_string" "auth-encryption" {
    length = 64
    keepers = {
        namespace = var.namespace
    }
}

resource "random_string" "auth-signature" {
    length = 64
    keepers = {
        namespace = var.namespace
    }
}
