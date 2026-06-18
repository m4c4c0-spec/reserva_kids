package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.ClienteDtos.*;
import cl.reservakids.application.usecase.ClienteAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Registro/login de cuentas de cliente (apoderados). Público + rate-limited. */
@RestController
@RequestMapping("/api/cliente-auth")
@RequiredArgsConstructor
public class ClienteAuthController {

    private final ClienteAuthService clienteAuthService;

    @PostMapping("/register")
    public ResponseEntity<ClienteTokenResponse> register(@Valid @RequestBody RegistroClienteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteAuthService.registrar(req));
    }

    @PostMapping("/login")
    public ClienteTokenResponse login(@Valid @RequestBody LoginClienteRequest req) {
        return clienteAuthService.login(req);
    }
}
