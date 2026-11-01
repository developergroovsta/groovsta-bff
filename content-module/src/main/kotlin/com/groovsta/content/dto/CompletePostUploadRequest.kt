package com.groovsta.content.dto

import jakarta.validation.constraints.Size

data class CompletePostUploadRequest(
    @Size(min = 1, max = 10)
    val objectKeys: List<String>
)
