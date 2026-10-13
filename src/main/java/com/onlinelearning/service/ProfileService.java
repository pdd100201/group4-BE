package com.onlinelearning.service;

import com.onlinelearning.dto.profile.ChangePasswordRequest;
import com.onlinelearning.dto.profile.ProfileResponse;
import com.onlinelearning.dto.profile.UpdateProfileRequest;

public interface ProfileService {
    ProfileResponse current();
    ProfileResponse update(UpdateProfileRequest request);
    void changePassword(ChangePasswordRequest request);
}
