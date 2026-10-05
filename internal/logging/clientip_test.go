package logging_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spongepowered/systemofadownload/internal/logging"
)

func TestProxyHeaders(t *testing.T) {
	const trustedPeer = "10.42.1.194:46930"
	cases := []struct {
		name, peer, xff, realIP, proto string
		wantAddr, wantScheme, wantXFF  string
	}{
		{"single client", trustedPeer, "203.0.113.7", "", "", "203.0.113.7:0", "http", "203.0.113.7"},
		{"trusted hop skipped", trustedPeer, "203.0.113.7, 10.42.0.5", "", "", "203.0.113.7:0", "http", "203.0.113.7"},
		{"rightmost untrusted wins", trustedPeer, "1.2.3.4, 203.0.113.7", "", "", "203.0.113.7:0", "http", "203.0.113.7"},
		{"untrusted peer ignored", "198.51.100.9:1234", "1.2.3.4", "", "https", "198.51.100.9:1234", "http", ""},
		{"forwarded proto", trustedPeer, "", "", "https", trustedPeer, "https", ""},
		{"no headers", trustedPeer, "", "", "", trustedPeer, "http", ""},
		{"x-real-ip fallback", trustedPeer, "10.42.0.5", "203.0.113.7", "", "203.0.113.7:0", "http", "203.0.113.7"},
		{"ipv6 client", trustedPeer, "2001:db8::1", "", "", "[2001:db8::1]:0", "http", "2001:db8::1"},
	}
	mw := logging.ProxyHeaders("10.42.0.0/16")
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var gotAddr, gotScheme, gotXFF string
			h := mw(http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
				gotAddr, gotScheme = r.RemoteAddr, "http"
				gotXFF = r.Header.Get("X-Forwarded-For")
				if r.TLS != nil {
					gotScheme = "https"
				}
			}))
			r := httptest.NewRequest(http.MethodGet, "/", http.NoBody)
			r.RemoteAddr = tc.peer
			for k, v := range map[string]string{"X-Forwarded-For": tc.xff, "X-Real-IP": tc.realIP, "X-Forwarded-Proto": tc.proto} {
				if v != "" {
					r.Header.Set(k, v)
				}
			}
			h.ServeHTTP(httptest.NewRecorder(), r)
			if gotAddr != tc.wantAddr || gotScheme != tc.wantScheme || gotXFF != tc.wantXFF {
				t.Errorf("got (%q, %q, XFF %q), want (%q, %q, XFF %q)",
					gotAddr, gotScheme, gotXFF, tc.wantAddr, tc.wantScheme, tc.wantXFF)
			}
		})
	}

	t.Run("unset disables", func(t *testing.T) {
		var gotAddr string
		next := http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) { gotAddr = r.RemoteAddr })
		r := httptest.NewRequest(http.MethodGet, "/", http.NoBody)
		r.RemoteAddr = trustedPeer
		r.Header.Set("X-Forwarded-For", "203.0.113.7")
		logging.ProxyHeaders(" ")(next).ServeHTTP(httptest.NewRecorder(), r)
		if gotAddr != trustedPeer {
			t.Errorf("got %q, want %q", gotAddr, trustedPeer)
		}
	})
}
