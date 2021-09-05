resource "kubernetes_secret" "play_secret" {
    metadata {
        name = local.play_secret_name
        namespace = var.namespace
    }
    data = {
        (var.play_secret_key) = random_password.play_secret.result
    }
}

resource "random_password" "play_secret" {
    length = 64
    special = true
}

# Create the production level configuration for our lagom
# applications
resource "kubernetes_secret" "lagom_application_config" {
    metadata {
        name = "lagom-${var.app_name}-config"
        namespace = var.namespace
        labels = {
            app = var.app_name
            environment = var.environment
        }
    }
    data = {
        "production.conf" = <<EOF
include "application.conf"

akka {
    loglevel = "DEBUG"
    actor.provider = cluster

    discovery {
        kubernetes-api {
            pod-namespace = "${var.namespace}"
            pod-namespace-path = "/var/run/secrets/kubernetes.io/serviceaccount/namespace"
        }
    }

    cluster {
        shutdown-after-unsuccessful-join-seed-nodes = 60s
        downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
    }
    management {
        cluster.bootstrap {
            contact-point-discovery {
                discovery-method = kubernetes-api
                service-name = "${var.app_name}"
                required-contact-point-nr = ${var.replica-count}
            }
        }
        health-checks {
            readiness-path = "health/ready"
            liveness-path = "health/alive"
        }
    }

}
play {
    server {
        pidfile.path = "/dev/null"
    }

    http.secret.key = $${APPLICATION_SECRET}
}

lagom {
  cluster.exit-jvm-when-system-terminated = on
  broker.kafka.service-name = $${${local.kafka_service_env}}
  persistence.jdbc.create-tables.auto = false

  akka.discovery {
    defaults {
      port-name = ${local.http_endpoint_name}
      port-protocol = tcp
      scheme = http
    }
    service-name-mappings {
      artifacts {
        lookup = artifacts-server.${var.namespace}.svc.cluster.local
      }
      auth {
        lookup = auth-server.${var.namespace}.svc.cluster.local
      }
      synchronizer {
        lookup = version-synchronizer.${var.namespace}.svc.cluster.local
      }
      versions {
        lookup = versions-server.${var.namespace}.svc.cluster.local
      }
      version-query {
        lookup = versions-query-server.${var.namespace}.svc.cluster.local
      }
      artifact-query {
        lookup = artifacts-query-server.${var.namespace}.svc.cluster.local
      }
    }
  }
}
lagom.cluster.exit-jvm-when-system-terminated = on

lagom.broker.kafka {
  service-name = $${${local.kafka_service_env}}
}

lagom.persistence.jdbc.create-tables.auto = false

http {
  address = $${?HTTP_BIND_ADDRESS}
}

db.default {
  async-executor {
    numThreads = 5
    minConnections = 5
    maxConnections = 5
  }
}

systemofadownload.auth.secrets {
  encryption = ${var.encryption_key}
  signature = ${var.signature_key}
}

# Any extra specified configurations for production based on the application
${var.extra_config}

EOF
    }
}
