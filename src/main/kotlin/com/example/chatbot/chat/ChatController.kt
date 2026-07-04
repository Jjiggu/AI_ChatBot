package com.example.chatbot.chat

import com.example.chatbot.common.ApiException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateChatRequest(
    @field:NotBlank(message = "question은 필수입니다")
    val question: String = "",
    val isStreaming: Boolean = false,
    val model: String? = null,
)

@RestController
class ChatController(private val chatService: ChatService) {

    @PostMapping("/api/chats")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: CreateChatRequest): ChatResponse {
        if (req.isStreaming) {
            // 스트리밍(A8)은 후속 단계 — 현재 미지원
            throw ApiException(HttpStatus.BAD_REQUEST, "NOT_SUPPORTED", "스트리밍 응답은 아직 지원하지 않습니다")
        }
        return chatService.create(UUID.fromString(jwt.subject), req.question, req.model)
    }
}
