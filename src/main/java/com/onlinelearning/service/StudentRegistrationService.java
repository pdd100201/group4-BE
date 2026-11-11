package com.onlinelearning.service;

import com.onlinelearning.dto.registration.*;
import org.springframework.data.domain.Page;

public interface StudentRegistrationService {
    StudentRegistrationResponse register(String email,RegistrationRequest request);
    StudentRegistrationResponse cancel(String email,Long id);
    StudentRegistrationResponse get(String email,Long id);
    Page<StudentRegistrationResponse> list(String email,StudentRegistrationFilter filter,int page,int size);
}
