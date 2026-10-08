package com.example.apigateway;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class GatewayController {
  @GetMapping("/api/health")
  public Map<String,String> health() { return Map.of("service","api-gateway","status","UP"); }
}
