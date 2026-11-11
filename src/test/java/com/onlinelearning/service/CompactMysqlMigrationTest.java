package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in: validates a migrated, disposable MySQL schema, never the source DB. */
@EnabledIfEnvironmentVariable(named="COMPACT30_MYSQL_TEST_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3306/codex_verify_compact30_.*")
@SpringBootTest(properties={
 "spring.datasource.url=${COMPACT30_MYSQL_TEST_URL}",
 "spring.datasource.username=root",
 "spring.datasource.password=${COMPACT30_MYSQL_PASSWORD}",
 "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
 "spring.jpa.hibernate.ddl-auto=validate",
 "app.seed-demo-users.enabled=false"
})
@ActiveProfiles("test") @Transactional(readOnly=true)
class CompactMysqlMigrationTest {
 @Autowired EntityManager em;
 @Autowired JdbcTemplate jdbc;

 @Test void migratedSchemaMatchesHibernateAndJsonDataCanBeRead() {
  assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=database() and table_type='BASE TABLE'",Integer.class)).isEqualTo(30);
  var policies=em.createQuery("select p from CertificatePolicy p",CertificatePolicy.class).getResultList();
  assertThat(policies).isNotEmpty();
  assertThat(policies.stream().mapToInt(p->p.getRequiredLessons().size()).sum()).isPositive();
  var answers=em.createQuery("select a from QuizAnswer a",QuizAnswer.class).getResultList();
  var selected=answers.stream().flatMap(a->a.getSelectedOptions().stream()).toList();
  assertThat(selected).isNotEmpty().allSatisfy(s->{
   assertThat(s.legacyId()).isNotNull();assertThat(s.questionOptionId()).isNotNull();
   assertThat(s.createdAt()).isNotNull();assertThat(s.updatedAt()).isNotNull();
  });
  assertThat(em.createQuery("select count(a) from AuditLog a",Long.class).getSingleResult())
   .isEqualTo(jdbc.queryForObject("select count(*) from action_logs where log_type='GENERAL'",Long.class));
  assertThat(em.createQuery("select count(a) from CertificateAuditLog a",Long.class).getSingleResult())
   .isEqualTo(jdbc.queryForObject("select count(*) from action_logs where log_type='CERTIFICATE'",Long.class));
  var discussions=em.createQuery("select d from LessonDiscussion d",LessonDiscussion.class).getResultList();
  assertThat(discussions).isNotEmpty().allSatisfy(d->{assertThat(d.getAuthor().getId()).isNotNull();assertThat(d.getLesson().getId()).isNotNull();});
 }
}
