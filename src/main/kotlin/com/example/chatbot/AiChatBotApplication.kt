package com.example.chatbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication
class AiChatBotApplication {

    /** 30분 규칙 등 시간 의존 로직 테스트를 위한 확장점 (Instant.now() 직접 호출 금지) */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
    runApplication<AiChatBotApplication>(*args)
}
