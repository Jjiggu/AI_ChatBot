package com.example.chatbot.feedback

import com.fasterxml.jackson.annotation.JsonProperty
import com.example.chatbot.domain.FeedbackStatus
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateFeedbackRequest(@param:JsonProperty("isPositive") val isPositive: Boolean)
data class UpdateFeedbackStatusRequest(val status: FeedbackStatus)

@RestController
class FeedbackController(private val feedbackService: FeedbackService) {

    @PostMapping("/api/chats/{chatId}/feedbacks")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable chatId: UUID,
        @RequestBody req: CreateFeedbackRequest,
    ): FeedbackResponse =
        feedbackService.create(UUID.fromString(jwt.subject), jwt.isAdmin(), chatId, req.isPositive)

    @GetMapping("/api/feedbacks")
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "createdAt,desc") sort: String,
        @RequestParam(required = false) isPositive: Boolean?,
    ): FeedbackPageResponse =
        feedbackService.list(UUID.fromString(jwt.subject), jwt.isAdmin(), page, size, sort, isPositive)

    @PatchMapping("/api/feedbacks/{feedbackId}/status")
    fun updateStatus(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable feedbackId: UUID,
        @RequestBody req: UpdateFeedbackStatusRequest,
    ): FeedbackResponse =
        feedbackService.updateStatus(jwt.isAdmin(), feedbackId, req.status)
}

private fun Jwt.isAdmin(): Boolean = getClaimAsString("role") == "ADMIN"
