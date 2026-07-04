package com.example.chatbot.feedback

import com.fasterxml.jackson.annotation.JsonProperty
import com.example.chatbot.domain.ChatRepository
import com.example.chatbot.domain.Feedback
import com.example.chatbot.domain.FeedbackRepository
import com.example.chatbot.domain.FeedbackStatus
import com.example.chatbot.domain.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class FeedbackResponse(
    val id: UUID,
    val userId: UUID,
    val chatId: UUID,
    @get:JsonProperty("isPositive")
    val isPositive: Boolean,
    val status: FeedbackStatus,
    val createdAt: Instant,
)

data class FeedbackPageResponse(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val feedbacks: List<FeedbackResponse>,
)

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {
    @Transactional
    fun create(userId: UUID, isAdmin: Boolean, chatId: UUID, isPositive: Boolean): FeedbackResponse {
        if (feedbackRepository.existsByUserIdAndChatId(userId, chatId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 피드백을 등록한 대화입니다")
        }
        val chat = chatRepository.findById(chatId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "대화를 찾을 수 없습니다") }
        if (!isAdmin && chat.thread.user.id != userId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "피드백 등록 권한이 없습니다")
        }
        return feedbackRepository.save(
            Feedback(
                user = userRepository.getReferenceById(userId),
                chat = chat,
                isPositive = isPositive,
                createdAt = clock.instant(),
            ),
        ).toResponse()
    }

    @Transactional(readOnly = true)
    fun list(
        userId: UUID,
        isAdmin: Boolean,
        page: Int,
        size: Int,
        sort: String,
        isPositive: Boolean?,
    ): FeedbackPageResponse {
        if (page < 0 || size < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상, size는 1 이상이어야 합니다")
        }
        val pageable = PageRequest.of(page, size, parseCreatedAtSort(sort))
        val feedbackPage = when {
            isAdmin && isPositive != null -> feedbackRepository.findByIsPositive(isPositive, pageable)
            isAdmin -> feedbackRepository.findAll(pageable)
            isPositive != null -> feedbackRepository.findByUserIdAndIsPositive(userId, isPositive, pageable)
            else -> feedbackRepository.findByUserId(userId, pageable)
        }
        return FeedbackPageResponse(
            page = feedbackPage.number,
            size = feedbackPage.size,
            totalElements = feedbackPage.totalElements,
            totalPages = feedbackPage.totalPages,
            feedbacks = feedbackPage.content.map { it.toResponse() },
        )
    }

    @Transactional
    fun updateStatus(isAdmin: Boolean, feedbackId: UUID, status: FeedbackStatus): FeedbackResponse {
        if (!isAdmin) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "피드백 상태 변경 권한이 없습니다")
        }
        val feedback = feedbackRepository.findById(feedbackId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "피드백을 찾을 수 없습니다") }
        feedback.status = status
        return feedback.toResponse()
    }

    companion object {
        private fun parseCreatedAtSort(sort: String): Sort {
            val parts = sort.split(",", limit = 2)
            if (parts[0] != "createdAt") {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sort는 createdAt만 지원합니다")
            }
            val direction = when (parts.getOrNull(1)?.lowercase() ?: "desc") {
                "asc" -> Sort.Direction.ASC
                "desc" -> Sort.Direction.DESC
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sort 방향은 asc 또는 desc만 지원합니다")
            }
            return Sort.by(direction, "createdAt")
        }
    }
}

private fun Feedback.toResponse() = FeedbackResponse(
    id = id!!,
    userId = user.id!!,
    chatId = chat.id!!,
    isPositive = isPositive,
    status = status,
    createdAt = createdAt,
)
