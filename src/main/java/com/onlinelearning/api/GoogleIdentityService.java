package com.onlinelearning.api;

public interface GoogleIdentityService {
    GoogleIdentity verify(String credential);

    record GoogleIdentity(String subject, String email, String fullName,
                          String pictureUrl, String hostedDomain) {
        public boolean isAuthoritativeForEmail() {
            return email.toLowerCase(java.util.Locale.ROOT).endsWith("@gmail.com")
                    || (hostedDomain != null && !hostedDomain.isBlank());
        }
    }
}
