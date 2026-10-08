package com.example.notificationservice;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final ConcurrentHashMap<String, Map<String,Object>> data = new ConcurrentHashMap<>();

  @GetMapping
  public Map<String,Object> list() {
    return Map.of("service","notification-service","count",data.size(),"items",data.values());
  }

  @GetMapping("/{id}")
  public Map<String,Object> get(@PathVariable String id) {
    Map<String,Object> item = data.get(id);
    if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
    return item;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> create(@RequestBody Map<String,Object> request) {
    if (request == null || request.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON body is required");
    Map<String,Object> item = new HashMap<>(request);
    String id = UUID.randomUUID().toString();
    item.put("id", id);
    item.put("service", "notification-service");
    item.put("createdAt", Instant.now().toString());
    data.put(id, Map.copyOf(item));
    return data.get(id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    if (data.remove(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
  }
}
