package com.example.chatbot.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}

interface ThreadRepository : JpaRepository<Thread, UUID>

interface ChatRepository : JpaRepository<Chat, UUID> {
    /** A3: 30분 규칙 판단용 — 유저의 가장 최근 chat */
    fun findTopByThreadUserIdOrderByCreatedAtDesc(userId: UUID): Chat?
    fun findByThreadIdOrderByCreatedAtAsc(threadId: UUID): List<Chat>
}

interface FeedbackRepository : JpaRepository<Feedback, UUID>