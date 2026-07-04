package com.example.chatbot.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

enum class Role { MEMBER, ADMIN }

enum class FeedbackStatus { PENDING, RESOLVED }

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    /** bcrypt 해시 저장용 (평문 저장 금지) */
    @Column(nullable = false)
    var password: String,

    @Column(nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role = Role.MEMBER,

    @Column(nullable = false)
    val createdAt: Instant,
)

@Entity
@Table(
    name = "threads",
    indexes = [Index(name = "idx_threads_user_created", columnList = "user_id, created_at")],
)
class Thread(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val createdAt: Instant,
)

@Entity
@Table(
    name = "chats",
    indexes = [Index(name = "idx_chats_thread_created", columnList = "thread_id, created_at")],
)
class Chat(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    val thread: Thread,

    @Column(nullable = false, columnDefinition = "text")
    val question: String,

    @Column(nullable = false, columnDefinition = "text")
    val answer: String,

    @Column(nullable = false)
    val model: String,

    @Column(nullable = false)
    val createdAt: Instant,
)

@Entity
@Table(
    name = "feedbacks",
    uniqueConstraints = [UniqueConstraint(name = "uk_feedbacks_user_chat", columnNames = ["user_id", "chat_id"])],
)
class Feedback(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    val chat: Chat,

    @Column(nullable = false)
    val isPositive: Boolean,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: FeedbackStatus = FeedbackStatus.PENDING,

    @Column(nullable = false)
    val createdAt: Instant,
)