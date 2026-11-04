package com.onlinelearning.service;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class CertificateDocumentTests {
 static final String CODE="0123456789ABCDEF0123456789ABCDEF";
 CertificateService.View example(String status){
  return new CertificateService.View(1L,1L,CODE,status,"Nguyễn Văn Bằng","Lập trình Java từ cơ bản đến nâng cao","Giảng viên Nguyễn Văn An","EduLearn - Hệ thống học trực tuyến",1,Instant.parse("2026-11-03T02:00:00Z"),null,null);
 }
 @Test void qrMatchesIndependentReportlabReference() throws Exception {
  var matrix=CertificateQr.encode("http://localhost:5173/verify-certificate?code="+CODE);
  try(var input=getClass().getResourceAsStream("/certificate-qr-v10-l-mask0.txt")){
   assertNotNull(input);var expected=new String(input.readAllBytes(),StandardCharsets.US_ASCII).lines().toList();
   assertEquals(57,expected.size());
   for(int y=0;y<57;y++)for(int x=0;x<57;x++)assertEquals(expected.get(y).charAt(x)=='1',matrix[y][x],"QR mismatch at "+x+","+y);
  }
 }
 @Test void generatesVietnamesePdfAndPreviewForVisualReview() throws Exception {
  var service=new CertificateDocumentService("http://localhost:5173");
  byte[] pdf=service.pdf(example("ISSUED"));assertTrue(pdf.length>10000);
  String structure=new String(pdf,StandardCharsets.ISO_8859_1);
  assertTrue(structure.startsWith("%PDF-1.4"));assertTrue(structure.endsWith("%%EOF\n"));
  assertTrue(structure.contains("/MediaBox [0 0 842 595]"));assertTrue(structure.contains("/Count 1"));
  Path folder=Path.of("target/certificate-qa");Files.createDirectories(folder);
  Files.write(folder.resolve("certificate-demo.pdf"),pdf);
  ImageIO.write(service.render(example("ISSUED")),"png",folder.resolve("certificate-source.png").toFile());
 }
 @Test void handlesLongUnicodeNamesWithoutClipping() throws Exception {
  var original=example("ISSUED");var longExample=new CertificateService.View(1L,1L,CODE,"ISSUED","Nguyễn Thị Hoàng Minh ".repeat(10).strip(),
   "Lập trình ứng dụng web với Java và cơ sở dữ liệu ".repeat(5).strip(),original.expertName(),"Trung tâm đào tạo công nghệ thông tin và phát triển kỹ năng phần mềm ".repeat(2).strip(),1,original.issuedAt(),null,null);
  var service=new CertificateDocumentService("https://learn.example.edu");
  assertDoesNotThrow(()->service.pdf(longExample));
  Path folder=Path.of("target/certificate-qa");Files.createDirectories(folder);
  Files.write(folder.resolve("certificate-long.pdf"),service.pdf(longExample));
 }
 @Test void blocksRevokedPdfAndUnsafeVerificationOrigins(){
  assertThrows(com.onlinelearning.exception.ApiException.class,()->new CertificateDocumentService("http://localhost:5173").pdf(example("REVOKED")));
  assertThrows(IllegalStateException.class,()->new CertificateDocumentService("javascript:invalid").verificationUrl(CODE));
  assertThrows(IllegalStateException.class,()->new CertificateDocumentService("https://example.com?token=secret").verificationUrl(CODE));
  assertThrows(IllegalArgumentException.class,()->CertificateQr.encode("x".repeat(272)));
 }
}
