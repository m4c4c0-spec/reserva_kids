package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.InvitadoDtos.*;
import cl.reservakids.domain.model.Invitado;
import cl.reservakids.domain.repository.InvitadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvitadoService {

    private final InvitadoRepository invitadoRepository;
    private static final SecureRandom RNG = new SecureRandom();

    @Transactional(readOnly = true)
    public List<InvitadoResponse> listar(Long tenantId, Long reservaId) {
        return invitadoRepository.findByReservaIdOrderByCreadoEn(reservaId).stream()
                .map(this::aResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvitadoResponse buscarPorToken(String token) {
        Invitado i = invitadoRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invitación no encontrada"));
        return aResponse(i);
    }

    @Transactional(readOnly = true)
    public InvitadoResumen resumen(Long tenantId, Long reservaId) {
        var todos = invitadoRepository.findByReservaIdOrderByCreadoEn(reservaId);
        long confirmados = todos.stream().filter(i -> Invitado.CONFIRMADO.equals(i.getEstado())).count();
        long rechazados = todos.stream().filter(i -> Invitado.RECHAZADO.equals(i.getEstado())).count();
        long pendientes = todos.stream().filter(i -> Invitado.PENDIENTE.equals(i.getEstado())).count();
        return new InvitadoResumen(todos.size(), confirmados, rechazados, pendientes);
    }

    @Transactional
    public InvitadoResponse agregar(Long tenantId, Long reservaId, InvitadoRequest req) {
        Invitado i = new Invitado();
        i.setTenantId(tenantId);
        i.setReservaId(reservaId);
        i.setNombre(req.nombre().trim());
        i.setEmail(req.email() != null ? req.email().trim() : null);
        i.setTelefono(req.telefono() != null ? req.telefono().trim() : null);
        i.setToken(generarToken());
        return aResponse(invitadoRepository.save(i));
    }

    @Transactional
    public void eliminar(Long tenantId, Long reservaId, Long invitadoId) {
        invitadoRepository.deleteById(invitadoId);
    }

    @Transactional
    public InvitadoResponse confirmarRsrv(String token, InvitadoRsrvRequest req) {
        Invitado i = invitadoRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invitación no encontrada"));
        if (!Invitado.PENDIENTE.equals(i.getEstado())) return aResponse(i);
        if (req.comentarios() != null && !req.comentarios().isBlank()) {
            i.setComentarios(req.comentarios().trim());
        }
        i.setEstado(Invitado.CONFIRMADO);
        return aResponse(invitadoRepository.save(i));
    }

    @Transactional
    public InvitadoResponse rechazarRsrv(String token, InvitadoRsrvRequest req) {
        Invitado i = invitadoRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invitación no encontrada"));
        if (!Invitado.PENDIENTE.equals(i.getEstado())) return aResponse(i);
        if (req.comentarios() != null && !req.comentarios().isBlank()) {
            i.setComentarios(req.comentarios().trim());
        }
        i.setEstado(Invitado.RECHAZADO);
        return aResponse(invitadoRepository.save(i));
    }

    private String generarToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private InvitadoResponse aResponse(Invitado i) {
        return new InvitadoResponse(
                i.getId(), i.getNombre(), i.getEmail(), i.getTelefono(),
                i.getEstado(), i.getToken(), i.getComentarios(), i.getCreadoEn());
    }
}
