package com.onlinelearning.dto.chat;

import com.onlinelearning.entity.User;
import java.util.List;

public record ChatUserResponse(Long id, String fullName, String avatarUrl, List<String> roles) {
    public static ChatUserResponse from(User user) {
        return new ChatUserResponse(user.getId(), user.getFullName(), user.getAvatarUrl(),
            user.getRoles().stream().map(role -> role.getCode()).sorted().toList());
    }
}
