

module "artifacts_server" {
    depends_on = [
        kubernetes_job.soad-liquibase-migration]
    source = "./lagom-base"
    environment = var.environment
    namespace = var.namespace
    replica-count = local.soad_app_configs.artifacts.image.replicas


    app_image = local.soad_app_configs.artifacts.image.image_name
    app_name = local.soad_app_configs.artifacts.service_name
    app_version = local.soad_app_configs.artifacts.image.image_version

    extra_config = local.soad_app_configs.artifacts.extra_config
    extra_envs = local.soad_app_configs.artifacts.extra_env
    extra_secret_envs = local.soad_app_configs.artifacts.extra_secret_envs
    kafka_topics = local.soad_app_configs.artifacts.kafka_topics

    encryption_key = local.encryption_key
    signature_key = local.signature_key
    cpu_max = "500m"
    memory_max = "512Mi"
}

module "artifacts_query_server" {
    depends_on = [
        kubernetes_job.soad-liquibase-migration,
        module.artifacts_server]
    source = "./lagom-base"
    environment = var.environment
    namespace = var.namespace
    app_name = local.soad_app_configs.artifacts_query.service_name

    replica-count = local.soad_app_configs.artifacts_query.image.replicas

    app_image = local.soad_app_configs.artifacts_query.image.image_name
    app_version = local.soad_app_configs.artifacts_query.image.image_version

    extra_config = local.soad_app_configs.artifacts_query.extra_config
    extra_envs = local.soad_app_configs.artifacts_query.extra_env
    extra_secret_envs = local.soad_app_configs.artifacts_query.extra_secret_envs

    encryption_key = local.encryption_key
    signature_key = local.signature_key
    cpu_max = "500m"
    memory_max = "500Mi"
}

module "auth_server" {
    source = "./lagom-base"
    namespace = var.namespace
    environment = var.environment
    app_name = local.soad_app_configs.auth.service_name

    app_image = local.soad_app_configs.auth.image.image_name
    app_version = local.soad_app_configs.auth.image.image_version
    replica-count = local.soad_app_configs.auth.image.replicas

    extra_config = local.soad_app_configs.auth.extra_config

    encryption_key = local.encryption_key
    signature_key = local.signature_key
    cpu_max = "200m"
    memory_max = "300Mi"

}

module "versions" {
    depends_on = [
        kubernetes_job.soad-liquibase-migration,
        module.artifacts_server]
    source = "./lagom-base"
    app_image = local.soad_app_configs.versions.image.name
    app_version = local.soad_app_configs.versions.image.version
    app_name = local.soad_app_configs.versions.service_name
    replica-count = local.soad_app_configs.versions.replicas
    kafka_topics = local.soad_app_configs.versions.kafka_topics
    extra_config = local.soad_app_configs.versions.extra_config
    extra_envs = local.soad_app_configs.versions.extra_env
    extra_secret_envs = local.soad_app_configs.versions.extra_secret_envs
    namespace = var.namespace
    environment = var.environment
    cpu_max = "1000m"
    cpu_min = "100m"
    memory_max = "1Gi"

    encryption_key = local.encryption_key
    signature_key = local.signature_key
}

module "versions_query" {
    depends_on = [
        kubernetes_job.soad-liquibase-migration,
        module.versions]
    source = "./lagom-base"
    app_image = local.soad_app_configs.versions_query.image.name
    app_version = local.soad_app_configs.versions_query.image.version
    app_name = local.soad_app_configs.versions_query.service_name
    replica-count = local.soad_app_configs.versions_query.replicas
    extra_config = local.soad_app_configs.versions_query.extra_config
    extra_envs = local.soad_app_configs.versions_query.extra_env
    extra_secret_envs = local.soad_app_configs.versions_query.extra_secret_env
    namespace = var.namespace
    environment = var.environment
    encryption_key = local.encryption_key
    signature_key = local.signature_key
    memory_max = "400Mi"
    cpu_max = "300m"
}

module "versions_syncrhonizer" {
    depends_on = [
        kubernetes_job.soad-liquibase-migration,
        module.versions_query,
        module.versions,
        module.artifacts_query_server]
    source = "./lagom-base"
    namespace = var.namespace
    environment = var.environment

    app_image = local.soad_app_configs.synchronizer.image.name
    memory_max = "2Gi"
    app_version = local.soad_app_configs.synchronizer.image.version
    app_name = local.soad_app_configs.synchronizer.service_name
    replica-count = local.soad_app_configs.synchronizer.replicas
    extra_envs = local.soad_app_configs.synchronizer.extra_env
    extra_secret_envs = local.soad_app_configs.synchronizer.extra_secret_env
    extra_ports = local.soad_app_configs.synchronizer.extra_ports
    encryption_key = local.encryption_key
    signature_key = local.signature_key
}
