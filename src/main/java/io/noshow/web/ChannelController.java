package io.noshow.web;

import io.noshow.domain.ChannelType;
import io.noshow.service.ChannelService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** Channel management UI: list, add, verify, delete. */
@Controller
public class ChannelController {

    private final ChannelService channels;

    public ChannelController(ChannelService channels) {
        this.channels = channels;
    }

    @GetMapping("/channels")
    public String list(Model model) {
        model.addAttribute("channels", channels.list());
        model.addAttribute("types", ChannelType.values());
        return "channels";
    }

    @PostMapping("/channels")
    public String create(@RequestParam ChannelType type, @RequestParam @NotBlank String config) {
        channels.create(type, config);
        return "redirect:/channels";
    }

    @PostMapping("/channels/{id}/verify")
    public String verify(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            channels.verify(id);
            redirect.addFlashAttribute("message", "Test sent and channel verified.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", "Could not verify: " + e.getMessage());
        }
        return "redirect:/channels";
    }

    @PostMapping("/channels/{id}/delete")
    public String delete(@PathVariable UUID id) {
        channels.delete(id);
        return "redirect:/channels";
    }
}
