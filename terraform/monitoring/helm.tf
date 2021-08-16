module "prometheus" {
    depends_on = [
        kubernetes_namespace.monitoring]
    source = "basisai/prometheus/helm"
    version = "6.0.1"

    chart_namespace = var.target_namespace
    # Prometheus server management
    server_pod_labels = {
        environment = var.environment
    }
    server_pv_size = "1Gi"
    server_security_context = {
        "fsGroup": 0,
        "runAsGroup": 0,
        "runAsNonRoot": false,
        "runAsUser": 0,
        "seccompProfile": {
            "type": "RuntimeDefault"
        }
    }

    # AlertManager - todo figure out alerting later
    alertmanager_chart_namespace = var.target_namespace
    alertmanager_pv_size = "256Mi"

    # NodeExporter - to monitor the pods cpu/memory etc.
    node_exporter_chart_namespace = var.target_namespace
    node_exporter_service_annotations = {
        "prometheus.io/scrape" = "true"
        "environment" = var.environment
    }

    # State Metrics
    kube_state_metrics_chart_namespace = var.target_namespace
    kube_state_metrics_collection_namespace = var.target_namespace

    pushgateway_pv_size = "256Mi"
}

module "grafana" {
    depends_on = [
        kubernetes_namespace.monitoring]
    source = "basisai/grafana/helm"
    version = "0.1.1"
    chart_namespace = var.target_namespace
    service_labels = {
        environment = var.environment
    }
    pod_annotations = {
        environment = var.environment
    }
    persistence_size = "1Gi"
    security_context = {
        "fsGroup": 0,
        "runAsGroup": 0,
        "runAsNonRoot": false,
        "runAsUser": 0,
        "seccompProfile": {
            "type": "RuntimeDefault"
        }
    }
}
