package com.groovsta.identity.web

import com.groovsta.identity.dto.response.ApiError
import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.exception.IdentityNotFoundException
import com.groovsta.identity.exception.InvalidCredentialsException
import com.groovsta.identity.exception.InvalidExternalIdentityException
import com.groovsta.identity.exception.InvalidExternalRegistrationException
import com.groovsta.identity.exception.InvalidVerificationException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException::class)
    fun invalidCredentials(exception: InvalidCredentialsException) = error(
        HttpStatus.UNAUTHORIZED,
        "INVALID_CREDENTIALS",
        exception.message ?: "Authentication failed"
    )

    @ExceptionHandler(InvalidExternalIdentityException::class)
    fun invalidExternalIdentity(exception: InvalidExternalIdentityException) = error(
        HttpStatus.UNAUTHORIZED,
        "INVALID_EXTERNAL_IDENTITY",
        exception.message ?: "Google authentication failed"
    )

    @ExceptionHandler(InvalidExternalRegistrationException::class)
    fun invalidExternalRegistration(exception: InvalidExternalRegistrationException) = error(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "INVALID_EXTERNAL_REGISTRATION",
        exception.message ?: "Google signup registration is invalid"
    )

    @ExceptionHandler(IdentityNotFoundException::class)
    fun notFound(exception: IdentityNotFoundException) = error(
        HttpStatus.NOT_FOUND,
        "NOT_FOUND",
        exception.message ?: "Resource was not found"
    )

    @ExceptionHandler(NoResourceFoundException::class)
    fun noResourceFound(exception: NoResourceFoundException) = error(
        HttpStatus.NOT_FOUND,
        "NOT_FOUND",
        exception.message ?: "Resource was not found"
    )

    @ExceptionHandler(IdentityConflictException::class)
    fun conflict(exception: IdentityConflictException) = error(
        HttpStatus.CONFLICT,
        "CONFLICT",
        exception.message ?: "Resource already exists"
    )

    @ExceptionHandler(InvalidVerificationException::class)
    fun invalidVerification(exception: InvalidVerificationException) = error(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "INVALID_VERIFICATION",
        exception.message ?: "Verification failed"
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(exception: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fields = exception.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "Invalid value")
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid", fields)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraintViolation(exception: ConstraintViolationException): ResponseEntity<ApiError> {
        val fields = exception.constraintViolations.associate {
            it.propertyPath.toString() to it.message
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid", fields)
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingRequestHeaderException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class
    )
    fun malformedRequest(exception: Exception) = error(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        exception.message ?: "Request is invalid"
    )

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(exception: IllegalArgumentException) = error(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        exception.message ?: "Request is invalid"
    )

    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatus(exception: ResponseStatusException) = error(
        HttpStatus.valueOf(exception.statusCode.value()),
        exception.statusCode.toString().replace(' ', '_'),
        exception.reason ?: "Request failed"
    )

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<ApiError> {
        logger.error(
            "request.failed unexpected errorType={} message={}",
            exception.javaClass.name,
            exception.message,
            exception
        )
        return error(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred"
        )
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        fields: Map<String, String> = emptyMap()
    ): ResponseEntity<ApiError> = ResponseEntity.status(status).body(ApiError(code, message, Instant.now(), fields))

    private companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
