package com.example.chatbot.auth

import com.example.chatbot.domain.Role
import com.example.chatbot.domain.User
import com.example.chatbot.domain.UserRepository
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import java.time.Clock
import javax.crypto.spec.SecretKeySpec

/**
 * SPEC §5: JWT 필터 1개(스프링 내장 BearerTokenAuthenticationFilter) + SecurityFilterChain 1개, STATELESS.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(@Value("\${jwt.secret}") secret: String) {

    private val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build()

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(secretKey))

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val jwtConverter = JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter { jwt ->
                listOf(SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role")))
            }
        }
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
            }
            .oauth2ResourceServer { rs ->
                rs.jwt { it.jwtAuthenticationConverter(jwtConverter) }
                rs.authenticationEntryPoint { _, res, _ ->
                    writeError(res, HttpStatus.UNAUTHORIZED, "인증이 필요합니다")
                }
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, res, _ -> writeError(res, HttpStatus.UNAUTHORIZED, "인증이 필요합니다") }
                    .accessDeniedHandler { _, res, _ -> writeError(res, HttpStatus.FORBIDDEN, "권한이 없습니다") }
            }
        return http.build()
    }

    /** A2: 관리자는 시드 데이터로 1명 생성 */
    @Bean
    fun adminSeeder(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
        clock: Clock,
        @Value("\${admin.password}") adminPassword: String,
    ) = ApplicationRunner {
        if (userRepository.findByEmail("admin@example.com") == null) {
            userRepository.save(
                User(
                    email = "admin@example.com",
                    password = passwordEncoder.encode(adminPassword),
                    name = "admin",
                    role = Role.ADMIN,
                    createdAt = clock.instant(),
                ),
            )
        }
    }

    private fun writeError(res: HttpServletResponse, status: HttpStatus, message: String) {
        res.status = status.value()
        res.contentType = "application/json;charset=UTF-8"
        res.writer.write("""{"code":"${status.name}","message":"$message"}""")
    }
}
