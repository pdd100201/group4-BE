package com.onlinelearning.service;
import com.onlinelearning.entity.*; public interface CourseService { Course submit(Long courseId,String email); Course approve(Long courseId,String email); Course reject(Long courseId,String email,String reason); Course resubmit(Long courseId,String email); }
