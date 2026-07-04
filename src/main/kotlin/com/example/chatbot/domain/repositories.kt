package com.example.chatbot.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID>

interface ThreadRepository : JpaRepository<Thread, UUID>

interface ChatRepository : JpaRepository<Chat, UUID>

interface FeedbackRepository : JpaRepository<Feedback, UUID>