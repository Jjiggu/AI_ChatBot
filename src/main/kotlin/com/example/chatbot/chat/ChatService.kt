package com.example.chatbot.chat

import com.example.chatbot.domain.Chat
import com.example.chatbot.domain.ChatRepository
import com.example.chatbot.domain.Thread
import com.example.chatbot.domain.ThreadRepository
import com.example.chatbot.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ChatResponse(
    val id: UUID,
    val question: String,
    val answer: String,
    val model: String,
    val threadId: UUID,
    val createdAt: Instant,
)

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val threadRepository: ThreadRepository,
    private val userRepository: UserRepository,
    private val chatModelClient: ChatModelClient,
    private val clock: Clock,
) {
    @Transactional
    fun create(userId: UUID, question: String, model: String?): ChatResponse {
        val now = clock.instant()
        val resolvedModel = model ?: DEFAULT_MODEL // A9

        // A3: "유저의 가장 최근 chat" 기준 30분 규칙 (스레드별 아님)
        val lastChat = chatRepository.findTopByThreadUserIdOrderByCreatedAtDesc(userId)
        val thread =
            if (lastChat != null && Duration.between(lastChat.createdAt, now) < THREAD_WINDOW) {
                lastChat.thread
            } else {
                threadRepository.save(Thread(user = userRepository.getReferenceById(userId), createdAt = now))
            }

        // 기존 대화 이력 → (user, assistant) 메시지 배열
        val messages = chatRepository.findByThreadIdOrderByCreatedAtAsc(thread.id!!)
            .flatMap { listOf(ChatMessage("user", it.question), ChatMessage("assistant", it.answer)) } +
            ChatMessage("user", question)

        val answer = chatModelClient.complete(resolvedModel, messages)

        val chat = chatRepository.save(
            Chat(thread = thread, question = question, answer = answer, model = resolvedModel, createdAt = clock.instant()),
        )
        return ChatResponse(chat.id!!, chat.question, chat.answer, chat.model, thread.id!!, chat.createdAt)
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
        private val THREAD_WINDOW: Duration = Duration.ofMinutes(30)
    }
}
