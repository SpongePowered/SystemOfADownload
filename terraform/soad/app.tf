module "soad-artifacts" {
    source = "./artifacts"
    namespace = var.namespace
    environment = var.environment
    postgres_host = ""
    postgres_password = ""
    postgres_username = ""
    cluster_member_role = local.akka_cluster_member
}
