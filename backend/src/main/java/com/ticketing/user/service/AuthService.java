package com.ticketing.user.service;

import com.ticketing.common.exception.BusinessException;
import com.ticketing.config.JwtProperties;
import com.ticketing.config.JwtTokenProvider;
import com.ticketing.user.dto.*;
import com.ticketing.user.entity.User;
import com.ticketing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_DUPLICATED",
                    "이미 사용 중인 이메일입니다.");
        }

        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name());
        userRepository.save(user);

        return new SignupResponse(user.getId(), user.getEmail(), user.getName());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return new LoginResponse(token, "Bearer", jwtProperties.getExpirationMs() / 1000);
    }
}
