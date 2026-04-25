package com.filipnikolov.launchpad.deployhook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DeployHookTemplateController {

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @GetMapping(value = "/api/deploy-hook/curl-template", produces = "text/plain")
    public String template(@RequestParam String app) {
        return """
                # Export these first:
                #   LAUNCHPAD_SECRET=<your deploy-hook secret>
                #   IMAGE=<docker image, e.g. filipnikolov/%s:latest>
                PAYLOAD='{"app_name":"%s","image":"'"$IMAGE"'","repo_url":"","port":3000,"timestamp":'"$(date +%%s)000"'}'
                curl -X POST %s/deploy-hook \\
                  -H "X-Signature-256: sha256=$(printf '%%s' "$PAYLOAD" | openssl dgst -sha256 -hmac "$LAUNCHPAD_SECRET" | awk '{print $2}')" \\
                  -H "X-Launchpad-Trigger: manual" \\
                  -H "Content-Type: application/json" \\
                  -d "$PAYLOAD"
                """.formatted(app, app, publicBaseUrl);
    }
}
