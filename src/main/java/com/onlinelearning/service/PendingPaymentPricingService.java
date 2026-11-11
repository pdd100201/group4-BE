package com.onlinelearning.service;

import com.onlinelearning.dto.course.CoursePackageResponse;
import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** All callers hold the course lock before locking registrations. */
@Service @RequiredArgsConstructor @Transactional
public class PendingPaymentPricingService {
    private final PaymentRepository payments;
    private final TransactionRepository transactions;
    private final EntityManager em;

    public void syncCourse(Long courseId) {
        var pending=em.createQuery("select r from CourseRegistration r where r.course.id=:id and r.status=com.onlinelearning.entity.RegistrationStatus.PENDING_PAYMENT order by r.id",CourseRegistration.class)
            .setParameter("id",courseId).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        for(var r:pending) sync(r);
    }

    public void sync(CourseRegistration r) {
        if(r.getStatus()!=RegistrationStatus.PENDING_PAYMENT) return;
        var p=payments.findByRegistrationId(r.getId()).orElse(null);
        if(p!=null&&(p.getStatus()!=PaymentStatus.PENDING||!Set.of("SEPAY","SEPAY_TEST").contains(p.getProvider()))) return;
        var tx=p==null?null:transactions.findFirstByPaymentIdOrderByIdDesc(p.getId()).orElse(null);
        // Expiration is handled by PaymentService; repricing must never revive an expired QR.
        if(tx!=null&&("SEPAY_EXPIRED".equals(tx.getType())||("SEPAY_PENDING".equals(tx.getType())&&!Instant.now().isBefore(tx.getCreatedAt().plusSeconds(300))))) return;
        var pack=r.getCoursePackage();
        if(pack!=null) em.refresh(pack);
        var quote=pack==null?null:CoursePackageResponse.from(pack);
        boolean changed=quote!=null&&(!same(r.getAmount(),quote.finalPrice())||!same(r.getPriceSnapshot(),quote.price())
            ||!same(r.getDiscountSnapshot(),quote.discountAmount())||!same(r.getDiscountPercentSnapshot(),quote.discountPercent()));
        boolean blocked=r.getCourse().getStatus()!=CourseStatus.PUBLISHED||(pack!=null&&(!pack.isActive()||quote.finalPrice().signum()<=0));
        if((changed||blocked)&&tx!=null&&"SEPAY_PENDING".equals(tx.getType())) tx.setType("SEPAY_REPLACED");
        if(changed) {
            r.setPriceSnapshot(quote.price());r.setDiscountSnapshot(quote.discountAmount());
            r.setDiscountPercentSnapshot(quote.discountPercent());r.setAmount(quote.finalPrice());
            if(p!=null) p.setAmount(quote.finalPrice());
        }
    }

    public boolean payable(CourseRegistration r) {
        var p=r.getCoursePackage();
        return r.getCourse().getStatus()==CourseStatus.PUBLISHED&&(p==null||(p.isActive()&&CoursePackageResponse.from(p).finalPrice().signum()>0));
    }
    private boolean same(BigDecimal a,BigDecimal b) { return a==null?b==null:b!=null&&a.compareTo(b)==0; }
}
