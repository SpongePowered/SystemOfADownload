package frontend

import (
	"net/http"
	"time"
)

// Cookie names used to persist user preferences between visits. These match
// the query parameter names used on the downloads page so the canonical URL
// and cookie storage stay in sync.
const (
	cookiePreRelease = "prerelease"
	cookieAPIFilter  = "apifilter"
)

// Default preference values. These match the legacy Vue SPA defaults:
// pre-release MC versions hidden, and API-version filtering on (which hides
// early prototype builds that happen to share an MC version with a later
// stable API release).
const (
	defaultShowPreRelease = false
	defaultFilterAPI      = true
)

// prefCookieMaxAge is one year. Browsers treat this as a persistent cookie.
const prefCookieMaxAge = 365 * 24 * time.Hour

// Preferences is the per-request resolved user preference state. The zero
// value is NOT the defaults — callers should always construct via
// ReadPreferences.
type Preferences struct {
	// ShowPreRelease controls whether pre-release MC versions (those
	// containing a "-" like "1.21-pre1") appear in the version selector.
	ShowPreRelease bool
	// FilterAPI controls whether per-MC-version query modifiers are
	// applied when listing builds. When true, SpongeForge 1.12.2 only
	// shows API 7 builds (hiding early API 8 prototypes); when false,
	// all builds on that MC version are shown.
	FilterAPI bool
}

// ReadPreferences resolves the effective preferences for a request. Query
// parameters take precedence over cookies, which take precedence over
// package defaults. Any value other than "1" or "0" in a query param or
// cookie is ignored (falls through to the next source).
func ReadPreferences(r *http.Request) Preferences {
	prefs := Preferences{
		ShowPreRelease: defaultShowPreRelease,
		FilterAPI:      defaultFilterAPI,
	}

	// Cookies first — they are the stored default.
	if c, err := r.Cookie(cookiePreRelease); err == nil {
		if v, ok := parseBoolPref(c.Value); ok {
			prefs.ShowPreRelease = v
		}
	}
	if c, err := r.Cookie(cookieAPIFilter); err == nil {
		if v, ok := parseBoolPref(c.Value); ok {
			prefs.FilterAPI = v
		}
	}

	// Query parameters override cookies. Only explicit values count —
	// an absent parameter leaves the cookie-sourced value in place.
	q := r.URL.Query()
	if q.Has(cookiePreRelease) {
		if v, ok := parseBoolPref(q.Get(cookiePreRelease)); ok {
			prefs.ShowPreRelease = v
		}
	}
	if q.Has(cookieAPIFilter) {
		if v, ok := parseBoolPref(q.Get(cookieAPIFilter)); ok {
			prefs.FilterAPI = v
		}
	}

	return prefs
}

// WritePreferences persists preferences to cookies on the given response.
// Called from the POST /settings handler. Cookies are scoped to the entire
// site (Path=/) and marked SameSite=Lax so normal navigation carries them.
// HttpOnly is intentionally false — no JS reads them today, but leaving the
// door open costs nothing and keeps parity with future progressive
// enhancement.
func WritePreferences(w http.ResponseWriter, prefs Preferences) {
	setBoolCookie(w, cookiePreRelease, prefs.ShowPreRelease)
	setBoolCookie(w, cookieAPIFilter, prefs.FilterAPI)
}

func setBoolCookie(w http.ResponseWriter, name string, value bool) {
	cookie := &http.Cookie{
		Name:     name,
		Value:    boolPrefString(value),
		Path:     "/",
		MaxAge:   int(prefCookieMaxAge.Seconds()),
		SameSite: http.SameSiteLaxMode,
	}
	http.SetCookie(w, cookie)
}

func parseBoolPref(s string) (value, ok bool) {
	switch s {
	case "1":
		return true, true
	case "0":
		return false, true
	default:
		return false, false
	}
}

func boolPrefString(v bool) string {
	if v {
		return "1"
	}
	return "0"
}
