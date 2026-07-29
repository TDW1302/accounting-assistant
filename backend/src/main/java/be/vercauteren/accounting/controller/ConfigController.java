package be.vercauteren.accounting.controller;

import be.vercauteren.accounting.dto.AppConfigResponse;
import be.vercauteren.accounting.service.InboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final InboxService inboxService;

    @Value("${app.falco.enabled:false}")
    private boolean peppolEnabled;

    @GetMapping
    public AppConfigResponse getConfig() {
        return new AppConfigResponse(peppolEnabled, inboxService.countErrors());
    }
}
