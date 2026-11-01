package com.onlinelearning.service;

import com.onlinelearning.entity.NewsletterSubscription;
import com.onlinelearning.repository.NewsletterSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NewsletterSubscriptionService {
    private final NewsletterSubscriptionRepository subscriptions;

    @Transactional
    public boolean subscribe(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (subscriptions.existsByEmail(normalizedEmail)) return false;

        NewsletterSubscription subscription = new NewsletterSubscription();
        subscription.setEmail(normalizedEmail);
        subscriptions.save(subscription);
        return true;
    }
}
