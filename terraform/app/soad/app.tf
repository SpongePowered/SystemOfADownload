module "system_of_a_download_services" {
    source = "./lagom-base"
    // We can deploy each of the normal services this way
    for_each = var.lagom_services
    app_image = var.soad_images[each.key].image_name
    app_name = var.soad_images[each.key].service_name
    app_version = each.value.image_version

    extra_envs = local.soad_extra_environment_variables[each.key]
    extra_secret_envs = local.secret_based_envs[each.key]
    extra_config = local.extra_configs[each.key]
    classpath = local.application_classpaths[each.key]

    namespace = var.namespace
    play_secret_key = "play_secret"
    environment = var.environment
}


