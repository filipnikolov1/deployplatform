package dev.filipnikolov.vector.setup.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/api/setup/templates")
public class SetupTemplatesController {

    private static final Logger log = LoggerFactory.getLogger(SetupTemplatesController.class);

    private static final Set<String> ALLOWED_LANGS = Set.of(
            "nextjs", "node", "springboot", "python", "go", "custom"
    );

    /**
     * Returns a rendered GitHub Actions CI workflow for the given language, app name, and branch.
     * Template markers @@app@@ and @@branch@@ are substituted. GitHub Actions ${{ ... }} syntax
     * is left untouched because we only replace the custom @@...@@ delimiters.
     */
    @GetMapping("/ci")
    public ResponseEntity<String> ciTemplate(
            @RequestParam String lang,
            @RequestParam(defaultValue = "my-app") String app,
            @RequestParam(defaultValue = "main") String branch) {

        if (!ALLOWED_LANGS.contains(lang)) {
            return ResponseEntity.badRequest().body("Unknown language: " + lang);
        }

        String raw = loadTemplate("setup-templates/ci/" + lang + ".yml.template");
        if (raw == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Template not found for language: " + lang);
        }

        String rendered = raw
                .replace("@@app@@", sanitize(app))
                .replace("@@branch@@", sanitize(branch));

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(rendered);
    }

    /**
     * Returns a rendered Dockerfile for the given language.
     */
    @GetMapping("/dockerfile")
    public ResponseEntity<String> dockerfileTemplate(@RequestParam String lang) {
        if (!ALLOWED_LANGS.contains(lang)) {
            return ResponseEntity.badRequest().body("Unknown language: " + lang);
        }

        String raw = loadTemplate("setup-templates/dockerfile/" + lang + ".dockerfile.template");
        if (raw == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Template not found for language: " + lang);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(raw);
    }

    private String loadTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load template: {}", path, e);
            return null;
        }
    }

    /** Strip characters that could cause template injection. */
    private String sanitize(String value) {
        if (value == null) return "";
        // Allow alphanumeric, dots, hyphens, underscores, slashes (for branch names like feature/x)
        return value.replaceAll("[^a-zA-Z0-9._/\\-]", "");
    }
}
