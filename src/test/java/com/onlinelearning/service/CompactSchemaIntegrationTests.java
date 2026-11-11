package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:compact_schema_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test") @Transactional
class CompactSchemaIntegrationTests {
 @Autowired JdbcTemplate jdbc;
 @Autowired UserRepository users;
 @Autowired PasswordResetTokenRepository resetTokens;
 @Autowired EmailVerificationTokenRepository verificationTokens;

 @Test void hibernateCreatesExactlyThirtyRealTables() {
  var tables=jdbc.queryForList("select lower(table_name) from information_schema.tables where table_schema='PUBLIC' and table_type='BASE TABLE'",String.class);
  assertThat(tables).hasSize(30).contains("discussion_entries","stored_assets","action_logs","auth_tokens")
   .doesNotContain("post_comment","lesson_comments","media_asset","chat_attachments","quiz_answer_option","certificate_policy_lessons","certificate_policy_quizzes");
 }

 @Test void tokenTypesAreIsolatedEvenWhenHashesMatch() {
  var user=users.findByEmailIgnoreCase("student@onlinelearning.local").orElseThrow();
  var reset=new PasswordResetToken();reset.setUser(user);reset.setTokenHash("a".repeat(64));reset.setExpiresAt(Instant.now().plusSeconds(600));
  var verification=new EmailVerificationToken();verification.setUser(user);verification.setTokenHash(reset.getTokenHash());verification.setExpiresAt(reset.getExpiresAt());
  resetTokens.saveAndFlush(reset);verificationTokens.saveAndFlush(verification);
  assertThat(resetTokens.findByTokenHash(reset.getTokenHash()).orElseThrow().getId()).isEqualTo(reset.getId());
  assertThat(verificationTokens.findByTokenHash(reset.getTokenHash()).orElseThrow().getId()).isEqualTo(verification.getId());
  resetTokens.deleteByUserAndUsedAtIsNull(user);resetTokens.flush();
  assertThat(resetTokens.findByTokenHash(reset.getTokenHash())).isEmpty();
  assertThat(verificationTokens.findByTokenHash(reset.getTokenHash())).isPresent();
 }
}
