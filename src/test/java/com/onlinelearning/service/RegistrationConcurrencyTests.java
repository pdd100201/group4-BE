package com.onlinelearning.service;

import com.onlinelearning.dto.course.*;
import com.onlinelearning.dto.registration.RegistrationRequest;
import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:registration_concurrency_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test") @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class RegistrationConcurrencyTests {
    @Autowired PlatformTransactionManager transactions;
    @Autowired ExpertCourseService expert;
    @Autowired CoursePackageService packages;
    @Autowired StudentRegistrationService registrations;
    @Autowired CourseRepository courses;
    @Autowired CourseRegistrationRepository repository;

    @Test void concurrentDuplicateSubmissionsCreateOnlyOnePendingRegistration() throws Exception {
        Long[] ids=new TransactionTemplate(transactions).execute(status->{
            Long id=expert.create("expert@onlinelearning.local",new CourseRequest("Concurrent course","Description","Java",BigDecimal.ZERO)).id();
            var input=new CoursePackagesRequest(List.of(PackageDuration.values()).stream().map(d->new CoursePackagesRequest.PackageInput(d,new BigDecimal("100000"),BigDecimal.ZERO,true)).toList());
            Long packageId=packages.update(id,"expert@onlinelearning.local",input).get(0).id();
            courses.findById(id).orElseThrow().setStatus(CourseStatus.PUBLISHED);
            return new Long[]{id,packageId};
        });
        assertNotNull(ids);
        var request=new RegistrationRequest(ids[0],ids[1],"Student concurrent","0912345678");
        CountDownLatch gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Long> create=()->{assertTrue(gate.await(10,TimeUnit.SECONDS));return registrations.register("student@onlinelearning.local",request).id();};
            var first=pool.submit(create);var second=pool.submit(create);gate.countDown();
            assertEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,repository.findAll((root,q,cb)->cb.equal(root.get("course").get("id"),ids[0])).size());
    }
}
