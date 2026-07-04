package com.example.chatbot.chat

import com.example.chatbot.domain.Chat
import com.example.chatbot.domain.ChatRepository
import com.example.chatbot.domain.FeedbackRepository
import com.example.chatbot.domain.Thread
import com.example.chatbot.domain.ThreadRepository
import com.example.chatbot.domain.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
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

data class ChatItemResponse(
    val id: UUID,
    val question: String,
    val answer: String,
    val model: String,
    val createdAt: Instant,
)

data class ThreadChatGroupResponse(
    val threadId: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val chats: List<ChatItemResponse>,
)

data class ChatThreadPageResponse(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val threads: List<ThreadChatGroupResponse>,
)

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val threadRepository: ThreadRepository,
    private val feedbackRepository: FeedbackRepository,
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

    @Transactional(readOnly = true)
    fun listThreads(userId: UUID, isAdmin: Boolean, page: Int, size: Int, sort: String): ChatThreadPageResponse {
        if (page < 0 || size < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상, size는 1 이상이어야 합니다")
        }
        val pageable = PageRequest.of(page, size, parseCreatedAtSort(sort))
        val threadPage = if (isAdmin) {
            threadRepository.findAll(pageable)
        } else {
            threadRepository.findByUserId(userId, pageable)
        }
        val threads = threadPage.content
        val chatsByThreadId = chatRepository.findByThreadIdInOrderByCreatedAtAsc(threads.mapNotNull { it.id })
            .groupBy { it.thread.id }

        return ChatThreadPageResponse(
            page = threadPage.number,
            size = threadPage.size,
            totalElements = threadPage.totalElements,
            totalPages = threadPage.totalPages,
            threads = threads.map { thread ->
                ThreadChatGroupResponse(
                    threadId = thread.id!!,
                    userId = thread.user.id!!,
                    createdAt = thread.createdAt,
                    chats = chatsByThreadId[thread.id].orEmpty().map { it.toItemResponse() },
                )
            },
        )
    }

    @Transactional
    fun deleteThread(userId: UUID, threadId: UUID) {
        val thread = threadRepository.findById(threadId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "스레드를 찾을 수 없습니다") }
        if (thread.user.id != userId) {
            // A6: 삭제는 관리자도 타인 스레드 삭제 불가
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "스레드 삭제 권한이 없습니다")
        }
        feedbackRepository.deleteByChatThreadId(threadId)
        chatRepository.deleteByThreadId(threadId)
        threadRepository.delete(thread)
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
        private val THREAD_WINDOW: Duration = Duration.ofMinutes(30)

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

private fun Chat.toItemResponse() = ChatItemResponse(
    id = id!!,
    question = question,
    answer = answer,
    model = model,
    createdAt = createdAt,
)
