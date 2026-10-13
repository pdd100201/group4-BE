package com.onlinelearning.service;

import com.onlinelearning.entity.User;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Arrays;

@Service @RequiredArgsConstructor
public class BlogActorService {
    private final UserRepository users;
    public User require(String email, String... roles) {
        User user = users.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại."));
        if (!user.isEnabled() || user.isBlocked()) throw new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không khả dụng.");
        if (user.getRoles().stream().noneMatch(r -> Arrays.asList(roles).contains(r.getCode())))
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.");
        return user;
    }
}
