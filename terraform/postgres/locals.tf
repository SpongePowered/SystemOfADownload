
locals {
    image = "postgres:13.4-alpine3.14"

    // Realistically, we don't need too much memory/resources for
    // postgres, this is after-all just a dumb app.
    // The units of measurement here are: 1000m = 1 cpu core
    // and the Mebibyte notation for base 2 instead of base 10 Megabyte.
    cpu = "100m"
    memory = "500Mi"
    cpu_max = "1000m"
    memory_max = "2Gi"
}
