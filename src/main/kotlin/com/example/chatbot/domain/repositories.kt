package com.example.chatbot.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}

interface ThreadRepository : JpaRepository<Thread, UUID> {
    fun findByUserId(userId: UUID, pageable: Pageable): Page<Thread>
}

interface ChatRepository : JpaRepository<Chat, UUID> {
    /** A3: 30분 규칙 판단용 — 유저의 가장 최근 chat */
    fun findTopByThreadUserIdOrderByCreatedAtDesc(userId: UUID): Chat?
    fun findByThreadIdOrderByCreatedAtAsc(threadId: UUID): List<Chat>
    fun findByThreadIdInOrderByCreatedAtAsc(threadIds: Collection<UUID>): List<Chat>
    fun deleteByThreadId(threadId: UUID)
}

interface FeedbackRepository : JpaRepository<Feedback, UUID> {
    fun deleteByChatThreadId(threadId: UUID)
}
