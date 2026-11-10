package io.noshow.web;

import io.noshow.domain.PingKind;
import io.noshow.service.PingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * The ingest endpoint: a job calls its ping URL to check in.
 *
 * <p>Both GET and POST are accepted so a plain {@code curl} works from a
 * crontab. Default kind is SUCCESS; {@code /ping/{token}/start} and
 * {@code /ping/{token}/fail} are convenience paths.
 */
@RestController
@RequestMapping("/ping")
public class PingController {

    private final PingService pings;

    public PingController(PingService pings) {
        this.pings = pings;
    }

    @RequestMapping(value = "/{token}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> success(@PathVariable String token, HttpServletRequest request) {
        return handle(token, PingKind.SUCCESS, request);
    }

    @RequestMapping(value = "/{token}/start", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> start(@PathVariable String token, HttpServletRequest request) {
        return handle(token, PingKind.START, request);
    }

    @RequestMapping(value = "/{token}/fail", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> fail(@PathVariable String token, HttpServletRequest request) {
        return handle(token, PingKind.FAIL, request);
    }

    private ResponseEntity<String> handle(String token, PingKind kind, HttpServletRequest request) {
        boolean matched = pings.recordPing(token, kind, clientIp(request), request.getHeader("User-Agent"));
        if (!matched) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok("ok");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
