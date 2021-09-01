module "system_of_a_download_services" {
    depends_on = [kubernetes_manifest.lagom-kafka]
    source = "./lagom-base"
    // We can deploy each of the normal services this way
    for_each = var.lagom_services
    app_image = var.soad_images[each.key].image_name
    app_name = var.soad_images[each.key].service_name
    app_version = var.soad_images[each.key].image_version
    replica-count = var.soad_images[each.key].replicas

    extra_envs = local.soad_extra_environment_variables[each.key]
    extra_secret_envs = local.secret_based_envs[each.key]
    extra_config = local.extra_configs[each.key]
    extra_java_opts = local.additional_java_opts[each.key]

    kafka_config = {
        host = ""
        service_name = "lagom-kafka-kafka-brokers"
        ports = []
    }

    namespace = var.namespace
    play_secret_key = "play_secret"
    environment = var.environment
}


