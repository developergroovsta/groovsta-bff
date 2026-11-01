package com.groovsta.identity.service

import com.groovsta.identity.dto.request.CompleteGoogleSignupRequest
import com.groovsta.identity.dto.request.GoogleAuthenticationRequest
import com.groovsta.identity.dto.response.AuthResponse
import com.groovsta.identity.dto.response.DeviceMetadata
import com.groovsta.identity.dto.response.GoogleAuthenticationResponse

interface GoogleAuthenticationService {
    fun authenticate(request: GoogleAuthenticationRequest, device: DeviceMetadata): GoogleAuthenticationResponse
    fun completeSignup(request: CompleteGoogleSignupRequest, device: DeviceMetadata): AuthResponse
}
