package com.example.chatbot.common

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

data class ErrorResponse(val code: String, val message: String)

/** 공통 에러 포맷 `{ code, message }` — 커스텀 예외 대신 ResponseStatusException 사용 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(e: ResponseStatusException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.valueOf(e.statusCode.value()), e.reason ?: "요청을 처리할 수 없습니다")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val msg = e.bindingResult.fieldErrors.firstOrNull()
            ?.let { "${it.field}: ${it.defaultMessage}" } ?: "잘못된 요청입니다"
        return error(HttpStatus.BAD_REQUEST, msg)
    }

    /** 중복 이메일 등 동시성으로 앱 체크를 뚫고 온 DB 유니크 위반 → 409 */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다")

    private fun error(status: HttpStatus, message: String) =
        ResponseEntity.status(status).body(ErrorResponse(status.name, message))
}
