package httpapi

import (
	"net/http"

	"github.com/spongepowered/systemofadownload/api"
)

// HTTPError represents an HTTP error that can be returned as a response
type HTTPError struct {
	StatusCode int
	Message    string
}

// Error implements the error interface
func (e HTTPError) Error() string {
	return e.Message
}

// BadRequestError is an HTTP 400 error for invalid or missing request data
type BadRequestError struct {
	HTTPError
}

// NewBadRequestError creates a new BadRequestError
func NewBadRequestError(message string) *BadRequestError {
	return &BadRequestError{
		HTTPError: HTTPError{
			StatusCode: http.StatusBadRequest,
			Message:    message,
		},
	}
}

// VisitRegisterArtifactResponse implements the RegisterArtifactResponseObject interface
func (e *BadRequestError) VisitRegisterArtifactResponse(w http.ResponseWriter) error {
	w.WriteHeader(e.StatusCode)
	return nil
}

// Ensure BadRequestError implements both error and RegisterArtifactResponseObject
var _ error = (*BadRequestError)(nil)
var _ api.RegisterArtifactResponseObject = (*BadRequestError)(nil)

// GroupNotFoundError is an HTTP 404 error for when a group is not found
type GroupNotFoundError struct {
	HTTPError
}

// NewGroupNotFoundError creates a new GroupNotFoundError
func NewGroupNotFoundError() *GroupNotFoundError {
	return &GroupNotFoundError{
		HTTPError: HTTPError{
			StatusCode: http.StatusNotFound,
			Message:    "group not found",
		},
	}
}

// VisitRegisterArtifactResponse implements the RegisterArtifactResponseObject interface
func (e *GroupNotFoundError) VisitRegisterArtifactResponse(w http.ResponseWriter) error {
	w.WriteHeader(e.StatusCode)
	return nil
}

// Ensure GroupNotFoundError implements both error and RegisterArtifactResponseObject
var _ error = (*GroupNotFoundError)(nil)
var _ api.RegisterArtifactResponseObject = (*GroupNotFoundError)(nil)

// ArtifactAlreadyExistsError is an HTTP 409 error for when an artifact already exists
type ArtifactAlreadyExistsError struct {
	HTTPError
}

// NewArtifactAlreadyExistsError creates a new ArtifactAlreadyExistsError
func NewArtifactAlreadyExistsError() *ArtifactAlreadyExistsError {
	return &ArtifactAlreadyExistsError{
		HTTPError: HTTPError{
			StatusCode: http.StatusConflict,
			Message:    "artifact already exists in the group",
		},
	}
}

// VisitRegisterArtifactResponse implements the RegisterArtifactResponseObject interface
func (e *ArtifactAlreadyExistsError) VisitRegisterArtifactResponse(w http.ResponseWriter) error {
	w.WriteHeader(e.StatusCode)
	return nil
}

// Ensure ArtifactAlreadyExistsError implements both error and RegisterArtifactResponseObject
var _ error = (*ArtifactAlreadyExistsError)(nil)
var _ api.RegisterArtifactResponseObject = (*ArtifactAlreadyExistsError)(nil)
