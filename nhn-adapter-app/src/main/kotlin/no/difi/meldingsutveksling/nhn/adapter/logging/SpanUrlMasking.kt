package no.difi.meldingsutveksling.nhn.adapter.logging

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.springframework.boot.reactor.netty.NettyServerCustomizer
import reactor.netty.http.server.HttpServer
import reactor.netty.http.server.HttpServerRequest

/** Semantic conventions for the server span, see https://opentelemetry.io/docs/specs/semconv/http/http-spans/ */
internal val URL_PATH: AttributeKey<String> = AttributeKey.stringKey("url.path")
internal val URL_QUERY: AttributeKey<String> = AttributeKey.stringKey("url.query")

/**
 * Masks the fødselsnummer in the URL attributes of the server span that the OpenTelemetry Java agent starts for each
 * request. The agent sets `url.path` to the raw path, e.g. `/nhn-adapter/api/lookup/fastlege-for:<fnr>`, and exports
 * the span to Tempo in dev and test. Setting the attribute again replaces the agent's value before the span ends and
 * is exported.
 *
 * Reactor Netty calls this for every request, with the raw request target, before Spring sees the request. So also a
 * request outside `spring.webflux.base-path`, one that Spring cannot parse, one that Spring Security rejects and one
 * that matches no route is masked. The agent makes the server span current while Reactor Netty calls this.
 *
 * Without the agent there is no current span, and this does nothing.
 */
class SpanUrlMasking(private val currentSpan: () -> Span = Span::current) : NettyServerCustomizer {
    override fun apply(server: HttpServer): HttpServer =
        server.mapHandle { handle, connection ->
            (connection as? HttpServerRequest)?.let { currentSpan().maskFnrInUrl(it.uri()) }
            handle
        }
}

/**
 * Masks the path and query of a raw request target, e.g. `/nhn-adapter/api/lookup/fastlege-for:<fnr>?x=1`, the way the
 * agent splits it into `url.path` and `url.query`. Only the attributes that had something to mask are set, so that an
 * attribute the agent left out stays out.
 */
internal fun Span.maskFnrInUrl(requestTarget: String) {
    val pathAndQuery = requestTarget.withoutSchemeAndAuthority().substringBefore('#')
    setIfMasked(URL_PATH, pathAndQuery.substringBefore('?'))
    if ('?' in pathAndQuery) {
        setIfMasked(URL_QUERY, pathAndQuery.substringAfter('?'))
    }
}

/** The scheme and authority of a request target in absolute form, `http://host/path`, as a proxy may send it. */
private val SCHEME_AND_AUTHORITY = Regex("""^[A-Za-z][A-Za-z0-9+.-]*://[^/?#]*""")

private fun String.withoutSchemeAndAuthority(): String =
    SCHEME_AND_AUTHORITY.find(this)?.let { "/" + substring(it.range.last + 1).removePrefix("/") } ?: this

private fun Span.setIfMasked(key: AttributeKey<String>, value: String) {
    val masked = value.maskFnrInUrl()
    if (masked != value) {
        setAttribute(key, masked)
    }
}
