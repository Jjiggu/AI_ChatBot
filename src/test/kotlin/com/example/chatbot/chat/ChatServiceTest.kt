package com.example.chatbot.chat

import com.example.chatbot.domain.Chat
import com.example.chatbot.domain.ChatRepository
import com.example.chatbot.domain.FeedbackRepository
import com.example.chatbot.domain.Thread
import com.example.chatbot.domain.ThreadRepository
import com.example.chatbot.domain.User
import com.example.chatbot.domain.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** SPEC §7: 30분 규칙 단위 테스트 — DB 무관, mock 리포지토리 + 고정 Clock */
class ChatServiceTest {

    private val chatRepository = mock(ChatRepository::class.java)
    private val threadRepository = mock(ThreadRepository::class.java)
    private val feedbackRepository = mock(FeedbackRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val chatModelClient = mock(ChatModelClient::class.java)

    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val service = ChatService(
        chatRepository, threadRepository, feedbackRepository, userRepository, chatModelClient, Clock.fixed(now, ZoneOffset.UTC),
    )

    private val userId = UUID.randomUUID()
    private val user = User(id = userId, email = "t@example.com", password = "x", name = "t", createdAt = now)
    private val newThreadId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        `when`(userRepository.getReferenceById(userId)).thenReturn(user)
        `when`(threadRepository.save(any(Thread::class.java))).thenAnswer { inv ->
            val t = inv.getArgument<Thread>(0)
            Thread(id = newThreadId, user = t.user, createdAt = t.createdAt)
        }
        `when`(chatModelClient.complete(anyString(), anyList())).thenReturn("답변")
        `when`(chatRepository.save(any(Chat::class.java))).thenAnswer { inv ->
            val c = inv.getArgument<Chat>(0)
            Chat(id = UUID.randomUUID(), thread = c.thread, question = c.question, answer = c.answer, model = c.model, createdAt = c.createdAt)
        }
    }

    private fun lastChatBefore(elapsed: Duration): Chat {
        val thread = Thread(id = UUID.randomUUID(), user = user, createdAt = now.minus(elapsed))
        return Chat(
            id = UUID.randomUUID(), thread = thread,
            question = "이전 질문", answer = "이전 답변", model = "gpt-4o-mini",
            createdAt = now.minus(elapsed),
        )
    }

    @Test
    fun `첫 질문이면 새 Thread를 생성한다`() {
        `when`(chatRepository.findTopByThreadUserIdOrderByCreatedAtDesc(userId)).thenReturn(null)

        val res = service.create(userId, "안녕", null)

        verify(threadRepository).save(any(Thread::class.java))
        assertEquals(newThreadId, res.threadId)
    }

    @Test
    fun `마지막 chat이 29분 59초 전이면 기존 Thread를 재사용한다`() {
        val lastChat = lastChatBefore(Duration.ofMinutes(29).plusSeconds(59))
        `when`(chatRepository.findTopByThreadUserIdOrderByCreatedAtDesc(userId)).thenReturn(lastChat)

        val res = service.create(userId, "안녕", null)

        verify(threadRepository, never()).save(any(Thread::class.java))
        assertEquals(lastChat.thread.id, res.threadId)
    }

    @Test
    fun `마지막 chat이 30분 1초 전이면 새 Thread를 생성한다`() {
        val lastChat = lastChatBefore(Duration.ofMinutes(30).plusSeconds(1))
        `when`(chatRepository.findTopByThreadUserIdOrderByCreatedAtDesc(userId)).thenReturn(lastChat)

        val res = service.create(userId, "안녕", null)

        verify(threadRepository).save(any(Thread::class.java))
        assertEquals(newThreadId, res.threadId)
        assertNotEquals(lastChat.thread.id, res.threadId)
    }
}
