package mtech.swe5006.peerconnect.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<?> root() {
        return ResponseEntity.ok(Map.of(
            "service", "PeerConnect Backend",
            "status", "running",
            "docs", "/swagger-ui.html",
            "openApi", "/v3/api-docs"
        ));
    }
}
