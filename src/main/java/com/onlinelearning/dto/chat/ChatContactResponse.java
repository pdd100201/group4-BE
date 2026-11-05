package com.onlinelearning.dto.chat;

public record ChatContactResponse(Long courseId, String courseTitle, ChatUserResponse peer) { }
