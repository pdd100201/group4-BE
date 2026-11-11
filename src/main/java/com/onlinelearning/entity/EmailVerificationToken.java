package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @DiscriminatorValue("EMAIL_VERIFICATION")

@Getter @Setter @NoArgsConstructor
public class EmailVerificationToken extends AuthToken {
}
