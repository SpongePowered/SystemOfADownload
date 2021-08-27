variable "inputs" {
    type = set(string)
    default = [
        "echo4",
        "echo2"]
}

resource "kubernetes_service" "dummy" {
    for_each = var.inputs
    metadata {
        name = each.value
    }
    spec {
        port {
            port = 80
            target_port = 5678
        }
        selector = {
            app = each.value
        }
    }
}

resource "kubernetes_deployment" "echo" {
    for_each = var.inputs
    metadata {
        name = each.value
    }
    spec {
        selector {
            match_labels = {
                app = each.value
            }
        }
        replicas = 2
        template {
            metadata {
                labels = {
                    app = each.value
                }
            }
            spec {
                container {
                    name = each.value
                    image = "hashicorp/http-echo"
                    args = [
                        "-text=${each.value}-sponge"
                    ]
                    port {
                        container_port = 5678
                    }
                }
            }
        }
    }
}

//resource "kubernetes_ingress" "echo-ingress" {
//    metadata {
//        name = "echo-ingress"
//        namespace = "default"
//        annotations = {
//            "kubernetes.io/ingress.class" = "nginx"
//            "nginx.ingress.kubernetes.io/ssl-redirect" = "false"
//        }
//
//    }
//    spec {
//        rule {
//            http {
//                path {
//                    path = "/echo4"
//                    backend {
//                        service_name = "echo4"
//                        service_port = 80
//                    }
//                }
//            }
//        }
//        rule {
//            http {
//                path {
//                    path = "/"
//                    backend {
//                        service_name = "echo2"
//                        service_port = 80
//                    }
//                }
//            }
//        }
//    }
//}
