package io.noshow.web;

import io.noshow.domain.Check;
import io.noshow.service.CheckService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dashboard: list checks and create new ones. Server-rendered with
 * Thymeleaf for Milestone 1; a richer UI can replace this without touching
 * the service layer.
 */
@Controller
public class CheckController {

    private final CheckService checks;

    public CheckController(CheckService checks) {
        this.checks = checks;
    }

    @GetMapping("/")
    public String dashboard(Model model, HttpServletRequest request) {
        List<Check> all = checks.listNewestFirst();
        List<UUID> ids = all.stream().map(Check::getId).toList();
        Map<UUID, Instant> lastTransitions = checks.lastTransitions(ids);
        model.addAttribute("checks", all);
        model.addAttribute("lastTransitions", lastTransitions);
        model.addAttribute("baseUrl", baseUrl(request));
        return "checks";
    }

    @GetMapping("/checks/{id}/events")
    public String history(@PathVariable UUID id, Model model) {
        Check check = checks.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such check"));
        model.addAttribute("check", check);
        model.addAttribute("events", checks.history(id));
        return "check-events";
    }

    @PostMapping("/checks")
    public String create(@RequestParam @NotBlank String name,
                         @RequestParam @Min(1) long periodSeconds,
                         @RequestParam(defaultValue = "0") @Min(0) long graceSeconds) {
        checks.create(name.trim(), periodSeconds, graceSeconds);
        return "redirect:/";
    }

    /** Scheme + host + port, so the dashboard can show full ping URLs. */
    private static String baseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return defaultPort
                ? scheme + "://" + host
                : scheme + "://" + host + ":" + port;
    }
}
