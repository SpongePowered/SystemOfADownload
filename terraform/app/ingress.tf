resource "kubernetes_ingress_v1" "versions-ingress" {
    metadata {
        name        = "versions-ingress"
        namespace   = var.namespace
        labels      = {
            app = "versions"
        }
        annotations = {
            "nginx.ingress.kubernetes.io/rewrite-target" = "/versions/$1"
            "nginx.ingress.kubernetes.io/ssl-redirect"  = "false"
        }
    }
    spec {
        rule {
            http {
                path {
                    path     = "/api/v2/(groups/[\\w\\.]+/artifacts/[\\w\\.]+/tags$)"
                    path_type = "Prefix"
                    backend {
                        service {
                            name = local.soad_app_configs.versions.service_name
                            port {
                                number = 80
                            }
                        }
                    }
                }
            }
        }
        rule {
            http {
                path {
                    path     = "/api/v2/(groups/[\\w\\.]+/artifacts/[\\w\\.]+/promotion$)"
                    path_type = "Prefix"
                    backend {
                        service {
                            name = local.soad_app_configs.versions.service_name
                            port {
                                number = 80
                            }
                        }
                    }
                }
            }
        }
    }
}

resource "kubernetes_ingress_v1" "artifacts-ingress" {

    metadata {
        name        = "artifacts-ingress"
        namespace   = var.namespace
        labels      = {
            app = "artifacts"
        }
        annotations = {
            "nginx.ingress.kubernetes.io/rewrite-target" = "/artifacts/$1"
            "nginx.ingress.kubernetes.io/ssl-redirect"   = "false"
        }
    }
    spec {
        rule {
            http {
                path {
                    path = "/api/v2/(groups$)"
                    path_type = "Prefix"
                    backend {
                        service {
                            name = local.soad_app_configs.artifacts.service_name
                            port {
                                number = 80
                            }
                        }
                    }
                }
            }
        }
        rule {
            http {
                path {
                    path     = "/api/v2/(groups/[\\w\\.]+$)"
                    path_type = "Prefix"
                    backend {
                        service {
                            name = local.soad_app_configs.versions.service_name
                            port {
                                number = 80
                            }
                        }
                    }
                }
            }
        }
        rule {
            http {
                path {
                    path     = "/api/v2/(groups/[\\w\\.]+/artifacts$)"
                    path_type = "Prefix"
                    backend {
                        service {
                            name = local.soad_app_configs.versions.service_name
                            port {
                                number = 80
                            }
                        }
                    }
                }
            }
        }
        rule {
            http {
                path {
                    path     = "/api/v2/(groups/[\\w\\.]+/artifacts/[\\w\\.]+/update$)"
                    path_type = "Prefix"
                    backend {
                        service {
                            name = local.soad_app_configs.versions.service_name
                            port {
                                number = 80
                            }
                        }
                    }
                }
            }
        }
    }
}

resource "kubernetes_ingress_v1" "auth-ingress" {
    metadata {
        name = "auth-ingress"
        namespace = var.namespace
        labels      = {
            app = "auth"
        }
        annotations = {
            "nginx.ingress.kubernetes.io/rewrite-target" = "/$1"
            "nginx.ingress.kubernetes.io/ssl-redirect"   = "false"
        }
    }

    spec {
        rule {
            http {
                path {
                    path = "/api/v2/(auth/login$)"
                    path_type = "Prefix"
                    backend {
                        service {
                            name = local.soad_app_configs.auth.service_name
                            port {
                                number = 80
                            }
                        }
                    }
                }
            }
        }
        rule {
            http {
                path {
                    path = "/api/v2/(auth/logout$)"
                    path_type = "Prefix"
                    backend {
                        service {
                            name = local.soad_app_configs.auth.service_name
                            port {
                                number = 80
                            }
                        }
                    }
                }
            }
        }
    }
}
