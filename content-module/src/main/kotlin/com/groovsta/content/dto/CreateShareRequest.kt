package com.groovsta.content.dto

import com.groovsta.content.model.ContentShareChannel

data class CreateShareRequest(
    val channel: ContentShareChannel = ContentShareChannel.OTHER
)
