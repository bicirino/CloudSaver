package com.cloudsaver.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class SupabaseAuthenticationToken extends AbstractAuthenticationToken {

    private final SupabaseUser principal;

    public SupabaseAuthenticationToken(SupabaseUser principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return principal != null ? principal.getToken() : null;
    }

    @Override
    public SupabaseUser getPrincipal() {
        return principal;
    }
}
