package com.onlinelearning.controller;

import com.onlinelearning.common.ApiMessage;
import com.onlinelearning.dto.profile.ChangePasswordRequest;
import com.onlinelearning.dto.profile.ProfileResponse;
import com.onlinelearning.dto.profile.UpdateProfileRequest;
import com.onlinelearning.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService service;

    @GetMapping
    public ProfileResponse current() { return service.current(); }

    @PatchMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return service.update(request);
    }

    @PostMapping("/change-password")
    public ApiMessage changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(request);
        return new ApiMessage("Password changed. Sign in again with your new password");
    }
}
