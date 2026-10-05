package logging

import (
	"crypto/tls"
	"net/http"
	"net/netip"
	"strings"
)

// ProxyHeaders rewrites r.RemoteAddr and the request scheme from forwarded
// headers so request logging (client.address, url.scheme, url.full) reports the
// real client. Headers are only honoured when the immediate peer is inside one
// of the comma-separated trusted CIDRs; otherwise they're ignored, since any
// client can send X-Forwarded-For. An empty cidrs disables the middleware.
// Panics on an invalid CIDR so misconfiguration fails at startup.
func ProxyHeaders(cidrs string) func(http.Handler) http.Handler {
	if strings.TrimSpace(cidrs) == "" {
		return func(next http.Handler) http.Handler { return next }
	}
	var trusted []netip.Prefix
	for c := range strings.SplitSeq(cidrs, ",") {
		trusted = append(trusted, netip.MustParsePrefix(strings.TrimSpace(c)))
	}
	isTrusted := func(ip netip.Addr) bool {
		for _, p := range trusted {
			if p.Contains(ip.Unmap()) {
				return true
			}
		}
		return false
	}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			// otelhttp takes client.address from the leftmost XFF entry, which
			// the client controls, so XFF is either dropped or collapsed to
			// the resolved client below.
			r = r.Clone(r.Context())
			peer, err := netip.ParseAddrPort(r.RemoteAddr)
			if err != nil || !isTrusted(peer.Addr()) {
				r.Header.Del("X-Forwarded-For")
				next.ServeHTTP(w, r)
				return
			}
			if ip, ok := clientFromHeaders(r.Header, isTrusted); ok {
				// Port 0 keeps RemoteAddr SplitHostPort-able; otelhttp omits
				// network.peer.port when it's 0.
				r.RemoteAddr = netip.AddrPortFrom(ip, 0).String()
				r.Header.Set("X-Forwarded-For", ip.String())
			} else {
				r.Header.Del("X-Forwarded-For")
			}
			// httplog and otelhttp derive the scheme from r.TLS alone; setting
			// r.URL.Scheme would double the host in httplog's url.full.
			if r.Header.Get("X-Forwarded-Proto") == "https" && r.TLS == nil {
				r.TLS = &tls.ConnectionState{}
			}
			next.ServeHTTP(w, r)
		})
	}
}

// clientFromHeaders walks X-Forwarded-For right to left, skipping trusted
// hops; the first untrusted entry is the client. An unparseable entry stops
// the walk (fail closed) and falls back to X-Real-IP.
func clientFromHeaders(h http.Header, isTrusted func(netip.Addr) bool) (netip.Addr, bool) {
	var hops []string
	for _, v := range h.Values("X-Forwarded-For") {
		hops = append(hops, strings.Split(v, ",")...)
	}
	for i := len(hops) - 1; i >= 0; i-- {
		ip, err := netip.ParseAddr(strings.TrimSpace(hops[i]))
		if err != nil {
			break
		}
		if !isTrusted(ip) {
			return ip.Unmap(), true
		}
	}
	if ip, err := netip.ParseAddr(strings.TrimSpace(h.Get("X-Real-IP"))); err == nil {
		return ip.Unmap(), true
	}
	return netip.Addr{}, false
}
