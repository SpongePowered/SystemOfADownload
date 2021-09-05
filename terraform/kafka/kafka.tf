
resource "kubernetes_manifest" "lagom-kafka" {
    manifest = {
        apiVersion = "kafka.strimzi.io/v1beta2"
        kind = "Kafka"
        metadata = {
            name = "lagom-kafka"
            namespace = var.namespace
        }
        spec = {
            kafka = {
                replicas = var.kafka_replicas
                listeners = [
                    {
                        name = "plain"
                        port = 9092
                        type = "internal"
                        tls = "false"
                    },
                    {
                        name = "external"
                        port = 9094
                        type = "nodeport"
                        tls = "false"
                    }
                ]
                storage = {
                    type = "jbod"
                    volumes = [{
                        id = 0
                        type = "persistent-claim"
                        size = "5Gi"
                        deleteClaim = "false"
                    }]
                }
                config = {
                    "offsets.topic.replication.factor" = 1
                    "transaction.state.log.replication.factor" = 1
                    "transaction.state.log.min.isr" = 1
                }
                template = {
                    pod = {
                        securityContext = {
                            runAsUser = 0
                            fsGroup = 0
                        }
                    }
                }
            }
            zookeeper = {
                replicas = var.zookeeper_replicas
                storage = {
                    type = "persistent-claim"
                    size = "2Gi"
                    deleteClaim = "false"
                }
                template = {
                    pod = {
                        securityContext = {
                            runAsUser = 0
                            fsGroup = 0
                        }
                    }
                }
            }
            entityOperator = {
                topicOperator = {}
                userOperator = {}
            }
        }
    }
}
