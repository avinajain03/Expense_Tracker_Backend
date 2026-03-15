package com.expensetracker.auth.service;

import com.expensetracker.auth.dto.AuthResponse;
import com.expensetracker.auth.dto.LoginRequest;
import com.expensetracker.auth.dto.RegisterRequest;
import com.expensetracker.auth.dto.TokenRefreshRequest;
import com.expensetracker.auth.jwt.JwtTokenProvider;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.user.model.User;
import com.expensetracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    @Lazy
    private final PasswordEncoder passwordEncoder;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException("Email already registered", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .age(request.getAge())
                .monthlyIncome(request.getMonthlyIncome())
                .build();

        User saved = userRepository.save(user);

        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException("Invalid email or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        return buildAuthResponse(user);
    }

    public AuthResponse refreshToken(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AppException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        // Issue a new access token; keep the same refresh token
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }
}
