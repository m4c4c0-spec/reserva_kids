package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.StaffDtos.*;
import cl.reservakids.application.usecase.StaffService;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Staff;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import cl.reservakids.infrastructure.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;
    private final JwtService jwtService;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @PostMapping("/login")
    public StaffTokenResponse login(@Valid @RequestBody StaffLoginRequest req) {
        Staff s = staffService.login(req.email(), req.password());
        String access = jwtService.emitirStaff(s);
        String refresh = jwtService.emitirRefreshStaff(s);
        return new StaffTokenResponse(access, refresh, s.getId(), s.getNombre(), s.getRol(), s.getTenantId());
    }

    @GetMapping("/eventos")
    public List<Map<String, Object>> eventos(@RequestParam String fecha,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        List<Reserva> reservas = staffService.eventosDelDia(principal.tenantId(), fecha);
        return reservas.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("hora", r.getInicio() != null ? r.getInicio().format(HH_MM) : null);
            m.put("numNinos", r.getNumNinos());
            m.put("comuna", r.getComuna());
            m.put("estado", r.getEstado().name());
            return m;
        }).toList();
    }
}
