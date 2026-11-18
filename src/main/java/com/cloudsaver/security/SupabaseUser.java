package com.cloudsaver.security;

import lombok.Builder;
import lombok.Getter;

import java.security.Principal;

@Getter
@Builder
public class SupabaseUser implements Principal {
    private String id;
    private String email;
    private String role;
    private String token;

    @Override
    public String getName() {
        return email != null && !email.isBlank() ? email : id;
    }
}
