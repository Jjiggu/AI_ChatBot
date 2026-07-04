package com.example.chatbot.feedback

import com.example.chatbot.domain.ChatRepository
import com.example.chatbot.domain.FeedbackRepository
import com.example.chatbot.domain.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeedbackServiceTest {

    private val feedbackRepository = mock(FeedbackRepository::class.java)
    private val chatRepository = mock(ChatRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = FeedbackService(
        feedbackRepository,
        chatRepository,
        userRepository,
        Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `같은 유저가 같은 chat에 피드백을 중복 생성하면 409`() {
        val userId = UUID.randomUUID()
        val chatId = UUID.randomUUID()
        `when`(feedbackRepository.existsByUserIdAndChatId(userId, chatId)).thenReturn(true)

        val e = assertFailsWith<ResponseStatusException> {
            service.create(userId, false, chatId, true)
        }

        assertEquals(HttpStatus.CONFLICT, e.statusCode)
    }
}
