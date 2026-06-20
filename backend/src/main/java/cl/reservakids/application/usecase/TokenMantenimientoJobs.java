package cl.reservakids.application.usecase;

import cl.reservakids.domain.repository.PasswordResetTokenRepository;
import cl.reservakids.domain.repository.RefreshTokenClienteRepository;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Mantenimiento diario (04:30): purga refresh tokens revocados/expirados (dueños y
 * clientes) y tokens de reset de contraseña usados/vencidos (falla 1.3) — ninguna
 * tabla crece sin límite.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenMantenimientoJobs {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenClienteRepository refreshTokenClienteRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Clock clock;

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purgarRefreshTokens() {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        int eliminados = refreshTokenRepository.purgarInvalidos(ahora);
        int clientes = refreshTokenClienteRepository.purgarInvalidos(ahora);
        int resets = passwordResetTokenRepository.purgarInvalidos(ahora);
        if (eliminados > 0 || clientes > 0 || resets > 0) {
            log.info("Mantenimiento: {} refresh tokens dueños, {} refresh tokens clientes y {} tokens de reset purgados",
                    eliminados, clientes, resets);
        }
    }
}
