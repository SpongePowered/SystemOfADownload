package soad

//go:generate mise exec oapi-codegen -- oapi-codegen -config oapi-codegen.yaml openapi.yaml
//go:generate mise x sqlc -- sqlc generate
//go:generate mise exec mockery -- mockery
