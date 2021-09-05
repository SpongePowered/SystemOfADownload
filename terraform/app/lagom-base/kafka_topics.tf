
resource "kubernetes_manifest" "lagom-service-topic" {
    for_each = var.kafka_topics
    manifest = {
        apiVersion = "kafka.strimzi.io/v1beta2"
        kind = "KafkaTopic"
        metadata = {
            name = each.key
            namespace = var.namespace
        }
        spec = {
            topicName = each.value.topic
        }
    }
}
