package com.example.chatbot

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@Disabled("P1: 실행 중인 DB 필요 — Testcontainers 셋업은 P1 범위 밖 (SPEC §5)")
@SpringBootTest
class AiChatBotApplicationTests {

    @Test
    fun contextLoads() {
    }

}
