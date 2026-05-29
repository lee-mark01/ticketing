package com.ticketing.user.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}
