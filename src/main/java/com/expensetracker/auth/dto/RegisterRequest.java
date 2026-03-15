package com.expensetracker.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "Password must contain uppercase, lowercase, digit, and special character"
    )
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotNull(message = "Age is required")
    @Min(value = 18, message = "Must be at least 18 years old")
    @Max(value = 100, message = "Age must be realistic")
    private Integer age;

    @NotNull(message = "Monthly income is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Monthly income must be positive")
    private Double monthlyIncome;
}
