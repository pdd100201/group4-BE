package com.onlinelearning.dto.registration;

import jakarta.validation.constraints.*;

public record RegistrationRequest(@NotNull @Positive Long courseId, @NotNull @Positive Long packageId,
        @NotBlank @Size(max=255) String fullName,
        @NotBlank @Pattern(regexp="[+0-9 ()-]{8,20}",message="Số điện thoại phải có 8–20 ký tự hợp lệ") String phone) {}
