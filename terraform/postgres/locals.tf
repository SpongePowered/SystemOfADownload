
locals {
    port = 5432
    image = "postgres:13.4-alpine3.14"

    // Realistically, we don't need too much memory/resources for
    // postgres, this is after-all just a dumb app.
    cpu = "100m"
    memory = "500Mi"
}
