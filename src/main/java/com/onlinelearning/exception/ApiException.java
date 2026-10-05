package com.onlinelearning.exception;
import org.springframework.http.*; public class ApiException extends RuntimeException { private final HttpStatus status; public ApiException(HttpStatus status,String message){super(message);this.status=status;} public HttpStatus status(){return status;} }
