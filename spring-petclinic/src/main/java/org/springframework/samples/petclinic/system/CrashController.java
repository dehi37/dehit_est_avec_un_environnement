package org.springframework.samples.petclinic.system;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CrashController {

    // ✅ AJOUTER CETTE MÉTHODE
    @GetMapping("/actuator/health")
    public ResponseEntity<String> health() {
        System.out.println("⚠️ Health check délibérément cassé !");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"status\":\"DOWN\"}");
    }

    @GetMapping("/oups")
    public String triggerException() {
        throw new RuntimeException("Expected: controller used to showcase what " +
                "happens when an exception is thrown");
    }
}