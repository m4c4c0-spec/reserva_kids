package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.ClienteDtos.NegocioResumen;
import cl.reservakids.application.usecase.DirectorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Área de cliente autenticado (rol CLIENTE). El directorio de negocios con disponibilidad. */
@RestController
@RequestMapping("/api/cliente")
@RequiredArgsConstructor
public class DirectorioController {

    private final DirectorioService directorioService;

    @GetMapping("/negocios")
    public List<NegocioResumen> negocios() {
        return directorioService.negociosConDisponibilidad();
    }
}
