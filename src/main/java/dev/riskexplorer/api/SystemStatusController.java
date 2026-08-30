package dev.riskexplorer.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

  @GetMapping("/status")
  public SystemStatus status() {
    return new SystemStatus("Repository Evolution Risk Explorer", "foundation-ready");
  }
}
