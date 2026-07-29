package be.vercauteren.accounting.controller;

import be.vercauteren.accounting.dto.InboxScanResult;
import be.vercauteren.accounting.service.InboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;

    @PostMapping("/scan")
    public InboxScanResult scan() {
        return inboxService.scan();
    }
}
