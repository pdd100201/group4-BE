package com.onlinelearning.config;

import com.onlinelearning.service.ChatAttachmentMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "app.chat.migrate-local", havingValue = "true")
public class ChatAttachmentMigrationRunner implements ApplicationRunner {
    private final ChatAttachmentMigrationService migration;
    private final ConfigurableApplicationContext context;
    @Value("${app.chat.migration-exit:false}") private boolean exitAfterMigration;
    @Override public void run(ApplicationArguments args) {
        var result = migration.migrateLocal();
        log.info("CHAT_CLOUDINARY_MIGRATION migrated={} failed={}", result.migrated(), result.failed());
        if (exitAfterMigration) context.close();
    }
}
