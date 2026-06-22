package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.ClienteDtos.NegocioResumen;
import cl.reservakids.application.usecase.DirectorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A4: directorio PÚBLICO de negocios agendables, SIN login.
 *
 * Antes el único listado vivía en {@code /api/cliente/negocios} (rol CLIENTE), así que un
 * apoderado que llegaba por la landing chocaba con un muro de registro antes de ver nada.
 * Este endpoint expone lo mismo que ya muestra la página pública del negocio: solo
 * {@code slug + nombre} (sin datos sensibles), reutilizando {@link DirectorioService}.
 *
 * La ruta literal {@code /api/public/negocios} tiene prioridad sobre el patrón
 * {@code /api/public/{slug}} de {@link PublicController} (Spring prefiere el match más
 * específico). "negocios" queda de hecho reservado como slug.
 */
@RestController
@RequestMapping("/api/public/negocios")
@RequiredArgsConstructor
public class PublicDirectorioController {

    private final DirectorioService directorioService;

    @GetMapping
    public List<NegocioResumen> negocios() {
        return directorioService.negociosConDisponibilidad();
    }
}
