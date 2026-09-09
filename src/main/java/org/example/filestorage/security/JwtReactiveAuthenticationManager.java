package org.example.filestorage.security;

import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtReactiveAuthenticationManager(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        return Mono.fromCallable(() -> jwtTokenProvider.parseToken(token))
                .map(claims -> {
                    AuthenticatedUser principal = new AuthenticatedUser(claims.userId(), claims.username(), claims.role());
                    return (Authentication) new UsernamePasswordAuthenticationToken(principal, token,
                            List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
                })
                .onErrorMap(JwtException.class, e -> new BadCredentialsException("Невалидный или просроченный токен", e));
    }
}
