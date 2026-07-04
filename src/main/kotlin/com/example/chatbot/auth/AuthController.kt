package com.example.chatbot.auth

import com.example.chatbot.domain.Role
import com.example.chatbot.domain.User
import com.example.chatbot.domain.UserRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class SignupRequest(
    @field:NotBlank @field:Email val email: String,
    // A5: 최소 8자
    @field:NotBlank @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다") val password: String,
    @field:NotBlank val name: String,
)

data class LoginRequest(val email: String, val password: String)

data class UserSummary(val id: UUID, val email: String, val name: String, val role: Role, val createdAt: Instant)

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtEncoder: JwtEncoder,
    private val clock: Clock,
) {
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody req: SignupRequest): UserSummary {
        if (userRepository.existsByEmail(req.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다")
        }
        val user = userRepository.save(
            User(
                email = req.email,
                password = passwordEncoder.encode(req.password), // A5: BCrypt
                name = req.name,
                role = Role.MEMBER, // A2: 가입은 항상 MEMBER
                createdAt = clock.instant(),
            ),
        )
        return UserSummary(user.id!!, user.email, user.name, user.role, user.createdAt)
    }

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): Map<String, String> {
        val user = userRepository.findByEmail(req.email)
        if (user == null || !passwordEncoder.matches(req.password, user.password)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다")
        }
        val now = clock.instant()
        val claims = JwtClaimsSet.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(24))) // A4: 24시간 고정 (스펙 고정값이라 설정 분리 안 함)
            .build()
        val token = jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims),
        ).tokenValue
        return mapOf("accessToken" to token)
    }
}
