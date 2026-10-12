package com.onlinelearning.common;

import com.onlinelearning.entity.PaymentStatus;
import com.onlinelearning.entity.Transaction;
import java.util.*;

public final class TransactionOutcome {
    private static final Map<String, PaymentStatus> STATUSES = Map.ofEntries(
            Map.entry("SEPAY_PENDING", PaymentStatus.PENDING), Map.entry("SEPAY_SUCCEEDED", PaymentStatus.SUCCEEDED),
            Map.entry("VNPAY_PENDING", PaymentStatus.PENDING), Map.entry("VNPAY_EXPIRED", PaymentStatus.FAILED),
            Map.entry("VNPAY_SUCCEEDED", PaymentStatus.SUCCEEDED), Map.entry("VNPAY_FAILED", PaymentStatus.FAILED),
            Map.entry("VNPAY_CANCELLED", PaymentStatus.CANCELLED));
    private TransactionOutcome() {}
    public static Set<String> knownTypes() { return STATUSES.keySet(); }
    public static List<String> typesFor(PaymentStatus status) {
        return STATUSES.entrySet().stream().filter(entry -> entry.getValue() == status).map(Map.Entry::getKey).toList();
    }
    public static PaymentStatus status(Transaction transaction) {
        return STATUSES.getOrDefault(transaction.getType(), transaction.getPayment().getStatus());
    }
    public static String provider(Transaction transaction) {
        return transaction.getType().startsWith("VNPAY_") ? "VNPAY" : transaction.getPayment().getProvider();
    }
}
