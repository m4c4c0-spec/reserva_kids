package cl.reservakids.application.dto;

import java.time.OffsetDateTime;

public final class AuditEventDtos {

    private AuditEventDtos() {}

    public record AuditEventResponse(
            OffsetDateTime creadoEn,
            String accion,
            String recursoTipo,
            Long recursoId,
            String detalle,
            String actorType) {}
}
