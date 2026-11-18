package com.cloudsaver.security;

import com.cloudsaver.service.SupabaseAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class SupabaseAuthFilter extends OncePerRequestFilter {

    private final SupabaseAuthService supabaseAuthService;

    public SupabaseAuthFilter(SupabaseAuthService supabaseAuthService) {
        this.supabaseAuthService = supabaseAuthService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            Optional<SupabaseUser> userOpt = supabaseAuthService.validateToken(authHeader);
            if (userOpt.isPresent()) {
                SupabaseUser user = userOpt.get();
                SupabaseAuthenticationToken authentication = new SupabaseAuthenticationToken(
                        user,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER"),
                                new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase())
                        )
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } else if (!supabaseAuthService.isAuthRequired()) {
            SupabaseUser devUser = supabaseAuthService.getFallbackDevUser();
            SupabaseAuthenticationToken devAuth = new SupabaseAuthenticationToken(
                    devUser,
                    List.of(
                            new SimpleGrantedAuthority("ROLE_USER"),
                            new SimpleGrantedAuthority("ROLE_DEV")
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(devAuth);
        }

        filterChain.doFilter(request, response);
    }
}
