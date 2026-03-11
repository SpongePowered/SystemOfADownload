package httpapi

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spongepowered/systemofadownload/api"
)

func TestHTTPErrors(t *testing.T) {
	t.Run("BadRequestError implements error interface", func(t *testing.T) {
		err := NewBadRequestError("test message")
		if err.Error() != "test message" {
			t.Errorf("Expected error message 'test message', got '%s'", err.Error())
		}
	})

	t.Run("BadRequestError implements RegisterArtifactResponseObject", func(t *testing.T) {
		var _ api.RegisterArtifactResponseObject = NewBadRequestError("test")
	})

	t.Run("BadRequestError returns 400 status code", func(t *testing.T) {
		err := NewBadRequestError("invalid request")
		w := httptest.NewRecorder()
		visitErr := err.VisitRegisterArtifactResponse(w)
		if visitErr != nil {
			t.Errorf("Expected no error from VisitRegisterArtifactResponse, got %v", visitErr)
		}
		if w.Code != http.StatusBadRequest {
			t.Errorf("Expected status code %d, got %d", http.StatusBadRequest, w.Code)
		}
	})

	t.Run("GroupNotFoundError implements error interface", func(t *testing.T) {
		err := NewGroupNotFoundError()
		if err.Error() != "group not found" {
			t.Errorf("Expected error message 'group not found', got '%s'", err.Error())
		}
	})

	t.Run("ArtifactAlreadyExistsError implements error interface", func(t *testing.T) {
		err := NewArtifactAlreadyExistsError()
		if err.Error() != "artifact already exists in the group" {
			t.Errorf("Expected error message 'artifact already exists in the group', got '%s'", err.Error())
		}
	})

	t.Run("GroupNotFoundError implements RegisterArtifactResponseObject", func(t *testing.T) {
		var _ api.RegisterArtifactResponseObject = NewGroupNotFoundError()
	})

	t.Run("ArtifactAlreadyExistsError implements RegisterArtifactResponseObject", func(t *testing.T) {
		var _ api.RegisterArtifactResponseObject = NewArtifactAlreadyExistsError()
	})

	t.Run("GroupNotFoundError returns 404 status code", func(t *testing.T) {
		err := NewGroupNotFoundError()
		w := httptest.NewRecorder()
		visitErr := err.VisitRegisterArtifactResponse(w)
		if visitErr != nil {
			t.Errorf("Expected no error from VisitRegisterArtifactResponse, got %v", visitErr)
		}
		if w.Code != http.StatusNotFound {
			t.Errorf("Expected status code %d, got %d", http.StatusNotFound, w.Code)
		}
	})

	t.Run("ArtifactAlreadyExistsError returns 409 status code", func(t *testing.T) {
		err := NewArtifactAlreadyExistsError()
		w := httptest.NewRecorder()
		visitErr := err.VisitRegisterArtifactResponse(w)
		if visitErr != nil {
			t.Errorf("Expected no error from VisitRegisterArtifactResponse, got %v", visitErr)
		}
		if w.Code != http.StatusConflict {
			t.Errorf("Expected status code %d, got %d", http.StatusConflict, w.Code)
		}
	})
}
