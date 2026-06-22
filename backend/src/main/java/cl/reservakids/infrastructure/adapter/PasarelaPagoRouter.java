package cl.reservakids.infrastructure.adapter;

import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.PasarelaPagoPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * V25: {@link PasarelaPagoPort} único que inyectan los casos de uso (ReservaService,
 * AgendaService). Delega al adapter que el dueño tenga activo en su tenant. Así se soportan
 * Mercado Pago y Khipu sin tocar la capa de aplicación ni el frontend/SolicitudesView
 * (el link de pago es agnóstico del proveedor).
 */
@Component
@Primary
@RequiredArgsConstructor
public class PasarelaPagoRouter implements PasarelaPagoPort {

    private final MercadoPagoAdapter mercadoPagoAdapter;
    private final KhipuAdapter khipuAdapter;

    @Override
    public PreferenciaPagoResponse crearPreferenciaDePago(Reserva reserva, Tenant tenant) {
        if ("KHIPU".equals(tenant.getPasarelaPago())) {
            return khipuAdapter.crearPreferenciaDePago(reserva, tenant);
        }
        return mercadoPagoAdapter.crearPreferenciaDePago(reserva, tenant);
    }
}