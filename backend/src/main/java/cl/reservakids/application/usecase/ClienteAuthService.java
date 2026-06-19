package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.ClienteDtos.*;
import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.CuentaCliente;
import cl.reservakids.domain.repository.CuentaClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Registro/login de cuentas de cliente (apoderados). Reusa BCrypt y el JWT del sistema,
 * pero emite tokens con rol CLIENTE y sin tenant. El email es identidad: se normaliza
 * (trim + minúsculas), igual que en {@link AuthService}.
 */
@Service
@RequiredArgsConstructor
public class ClienteAuthService {

    private final CuentaClienteRepository cuentaClienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenPort tokenPort;

    private static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Reusa la normalización E.164 chilena de {@link Cliente} y valida que sea un celular
     * plausible (56 + 9 dígitos). Sin esto, un número mal escrito haría fallar el WhatsApp
     * de confirmación/recordatorio en silencio cuando se conecte el proveedor real.
     */
    private static String normalizarTelefono(String raw) {
        String tel = Cliente.normalizarTelefono(raw);
        if (tel == null || !tel.matches("56\\d{9}")) {
            throw new IllegalArgumentException(
                    "Teléfono inválido: usa un celular chileno, ej. +56 9 1234 5678");
        }
        return tel;
    }

    @Transactional
    public ClienteTokenResponse registrar(RegistroClienteRequest req) {
        String email = normalizarEmail(req.email());
        if (cuentaClienteRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese email");
        }
        CuentaCliente cuenta = new CuentaCliente();
        cuenta.setEmail(email);
        cuenta.setNombre(req.nombre());
        cuenta.setTelefono(normalizarTelefono(req.telefono()));
        cuenta.setPasswordHash(passwordEncoder.encode(req.password()));
        cuenta = cuentaClienteRepository.save(cuenta);
        return tokenResponse(cuenta);
    }

    @Transactional(readOnly = true)
    public ClienteTokenResponse login(LoginClienteRequest req) {
        CuentaCliente cuenta = cuentaClienteRepository.findByEmail(normalizarEmail(req.email()))
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
        if (!passwordEncoder.matches(req.password(), cuenta.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
        return tokenResponse(cuenta);
    }

    private ClienteTokenResponse tokenResponse(CuentaCliente cuenta) {
        String token = tokenPort.emitirAccessTokenCliente(cuenta.getId(), cuenta.getEmail());
        return new ClienteTokenResponse(token, cuenta.getEmail(), cuenta.getNombre(), cuenta.getTelefono());
    }
}
