package org.example.filestorage.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.example.filestorage.model.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider =
            new JwtTokenProvider(new JwtProperties("test-secret-key-at-least-32-bytes-long", 3600000L));

    @Test
    void generatesTokenAndParsesItBack() {
        String token = provider.generateToken(42, "ivan", UserRole.ADMIN);

        JwtClaims claims = provider.parseToken(token);

        assertThat(claims.userId()).isEqualTo(42);
        assertThat(claims.username()).isEqualTo("ivan");
        assertThat(claims.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void parsingExpiredTokenThrows() {
        JwtTokenProvider expiredProvider =
                new JwtTokenProvider(new JwtProperties("test-secret-key-at-least-32-bytes-long", -1000L));
        String expiredToken = expiredProvider.generateToken(1, "ivan", UserRole.USER);

        assertThatThrownBy(() -> provider.parseToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parsingTokenSignedWithDifferentKeyThrows() {
        JwtTokenProvider otherProvider =
                new JwtTokenProvider(new JwtProperties("different-secret-key-at-least-32-bytes!!", 3600000L));
        String tokenFromOtherKey = otherProvider.generateToken(1, "ivan", UserRole.USER);

        assertThatThrownBy(() -> provider.parseToken(tokenFromOtherKey))
                .isInstanceOf(SignatureException.class);
    }
}
