package be.vercauteren.accounting.controller;

import be.vercauteren.accounting.dto.AiProviderRequest;
import be.vercauteren.accounting.dto.AuthResponse;
import be.vercauteren.accounting.dto.ChangePasswordRequest;
import be.vercauteren.accounting.dto.LoginRequest;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.service.AuthService;
import be.vercauteren.accounting.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me() {
        return authService.getCurrentUser()
            .map(user -> ResponseEntity.ok(new AuthResponse(
                userService.toResponse(user),
                userService.isPasswordExpired(user)
            )))
            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.getCurrentUser()
            .map(user -> {
                userService.changePassword(user.getUsername(), request);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/ai-provider")
    public ResponseEntity<Void> updateAiProvider(@Valid @RequestBody AiProviderRequest request) {
        return authService.getCurrentUser()
            .map(user -> {
                userService.updateAiProvider(user.getUsername(), request.aiProvider());
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
