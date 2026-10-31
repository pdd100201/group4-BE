# Unit test voi JUnit 5 va Mockito

Du an su dung Java 21 va Maven Wrapper, khong can cai Maven rieng.
`spring-boot-starter-test` trong `pom.xml` da cung cap JUnit Jupiter (JUnit 5),
Mockito Core va Mockito JUnit Jupiter. Version duoc Spring Boot quan ly.

## Chay test tren Windows

Mo terminal moi sau khi cai JDK va cap nhat JAVA_HOME:

```powershell
java -version
.\mvnw.cmd -version
.\test-unit.ps1
.\test-unit.ps1 -Test StudentEnrollmentServiceTest
.\mvnw.cmd test
```

`test-unit.ps1` mac dinh chi chay cac lop ket thuc bang `Test`.
Quy uoc: unit test moi dat ten `TenServiceTest.java`; cac test hien tai ket
thuc bang `Tests` van duoc chay boi lenh Maven `test` day du.
Bao cao nam trong `target/surefire-reports`.

## Viet test

Tao file trong `src/test/java`, theo package cua lop can test.
Tham khao `StudentEnrollmentServiceTest.java` de dung:

- `@ExtendWith(MockitoExtension.class)` de khoi tao Mockito.
- `@Mock` de gia lap repository hoac dich vu phu thuoc.
- `@InjectMocks` de tao service voi cac mock.
- `when(...).thenReturn(...)`, `assertThrows`, `assertEquals`, `verify(...)`.

Unit test service khong can `@SpringBootTest`, database, SMTP hay server dang chay.
Trong IntelliJ, Reload Maven Project va chon Project SDK/Maven Runner JRE la JDK 21,
sau do bam Run ben canh `@Test` hoac ten lop.
