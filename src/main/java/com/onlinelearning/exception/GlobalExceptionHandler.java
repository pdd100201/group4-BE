package com.onlinelearning.exception;
import org.springframework.http.*; import org.springframework.mail.MailException; import org.springframework.web.bind.annotation.*; import org.springframework.web.bind.MethodArgumentNotValidException; import java.time.*; import java.util.*;
@RestControllerAdvice public class GlobalExceptionHandler {
 @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e){return response(e.status(),e.getMessage());}
 @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, org.springframework.web.bind.MissingServletRequestParameterException.class})
 ResponseEntity<?> parameter(Exception e){return response(HttpStatus.BAD_REQUEST,"Invalid request parameter");}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){return response(HttpStatus.BAD_REQUEST,e.getBindingResult().getFieldErrors().get(0).getDefaultMessage());}
 @ExceptionHandler(MailException.class) ResponseEntity<?> mail(MailException e){return response(HttpStatus.SERVICE_UNAVAILABLE,"Email service is unavailable");}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) ResponseEntity<?> uploadSize(Exception e){return response(HttpStatus.PAYLOAD_TOO_LARGE,"Tệp tải lên vượt giới hạn 100 MB.");}
 @ExceptionHandler(Exception.class) ResponseEntity<?> other(Exception e){return response(HttpStatus.INTERNAL_SERVER_ERROR,"Unexpected server error");}
 private ResponseEntity<?> response(HttpStatus status,String message){return ResponseEntity.status(status).body(Map.of("timestamp",Instant.now(),"status",status.value(),"message",message));}
}
