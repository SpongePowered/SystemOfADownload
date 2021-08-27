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
        name = "lagom-application-config"
        namespace = var.namespace
        labels = {
            environment = var.environment
        }
    }
    data = {
        "production.conf" = <<EOF
include "application.conf"

play.modules.enabled += org.spongepowered.downloads.auth.AuthModule

akka {
    loglevel = "DEBUG"

    discovery {
        method = kubernetes-api
        kubernetes-api {
            pod-namespace = "${var.namespace}"
            pod-label-selector = "app=${var.app_name}"
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

    http.secret.key = "$${APPLICATION_SECRET}"
}

# Any extra specified configurations for production based on the application
${var.extra_config}

EOF
    }
}
