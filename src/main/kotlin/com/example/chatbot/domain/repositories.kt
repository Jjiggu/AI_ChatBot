package com.example.chatbot.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}

interface ThreadRepository : JpaRepository<Thread, UUID>

interface ChatRepository : JpaRepository<Chat, UUID>

interface FeedbackRepository : JpaRepository<Feedback, UUID>