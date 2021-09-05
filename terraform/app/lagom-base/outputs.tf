output "service_name" {
    value = kubernetes_service.lagom-services.metadata[0].name
}

output "lagom_service_address" {
    value = "${kubernetes_service.lagom-services.metadata[0].name}.${kubernetes_service.lagom-services.metadata[0].namespace}.svc.cluster.local"
}

output "lagom_service_mapping" {
    value = <<-EOF
    ${kubernetes_service.lagom-services.metadata[0].name} {
        lookup = "${kubernetes_service.lagom-services.metadata[0].name}.${kubernetes_service.lagom-services.metadata[0].namespace}.svc.cluster.local
    }
    EOF
}
