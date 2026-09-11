package com.minex.backend.web;

import com.minex.backend.service.DashboardService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** §4.5 dashboard/visualization data API. */
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return dashboard.categories();
    }

    @GetMapping("/categories/{id}/timeseries")
    public List<Map<String, Object>> timeseries(@PathVariable UUID id,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to) {
        return dashboard.timeseries(id, from, to);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return dashboard.summary();
    }
}
