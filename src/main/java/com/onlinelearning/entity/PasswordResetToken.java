package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @DiscriminatorValue("PASSWORD_RESET")  @Getter @Setter @NoArgsConstructor
public class PasswordResetToken extends AuthToken {     }
