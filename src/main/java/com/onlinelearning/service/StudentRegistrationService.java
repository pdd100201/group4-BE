package com.onlinelearning.service;

import com.onlinelearning.dto.registration.*;
import org.springframework.data.domain.Page;

public interface StudentRegistrationService {
    StudentRegistrationResponse register(String email,RegistrationRequest request);
    StudentRegistrationResponse get(String email,Long id);
    Page<StudentRegistrationResponse> list(String email,int page,int size);
}
