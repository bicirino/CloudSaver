package com.cloudsaver.controller;

import com.cloudsaver.service.SupabaseAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConfigController {

    private final SupabaseAuthService supabaseAuthService;

    public ConfigController(SupabaseAuthService supabaseAuthService) {
        this.supabaseAuthService = supabaseAuthService;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "supabaseUrl", supabaseAuthService.getSupabaseUrl(),
                "supabaseAnonKey", supabaseAuthService.getSupabaseAnonKey(),
                "authRequired", supabaseAuthService.isAuthRequired()
        ));
    }
}
