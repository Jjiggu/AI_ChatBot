package com.example.chatbot.chat

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException

/** OpenAI chat/completions 메시지 포맷과 1:1 대응 */
data class ChatMessage(val role: String, val content: String)

/** SPEC §5: LLM 추상화 — 향후 RAG 도입 시 이 인터페이스 뒤에 retrieval을 끼워넣는다 */
interface ChatModelClient {
    fun complete(model: String, messages: List<ChatMessage>): String
}

@Component
class OpenAiChatClient(
    @Value("\${openai.api-key}") apiKey: String, // A1: 환경변수 주입
    builder: RestClient.Builder,
) : ChatModelClient {

    private val restClient = builder
        .baseUrl("https://api.openai.com")
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
        .build()

    override fun complete(model: String, messages: List<ChatMessage>): String {
        val response = try {
            restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("model" to model, "messages" to messages))
                .retrieve()
                .body(OpenAiResponse::class.java)
        } catch (e: RestClientResponseException) {
            // OpenAI 4xx/5xx → 502 (공통 핸들러가 {code,message}로 변환)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI 호출 실패 (status ${e.statusCode.value()})")
        } catch (e: RestClientException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI 호출 실패")
        }
        return response?.choices?.firstOrNull()?.message?.content
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI 응답이 비어 있습니다")
    }

    data class OpenAiResponse(val choices: List<Choice> = emptyList())
    data class Choice(val message: Message)
    data class Message(val content: String?)
}
