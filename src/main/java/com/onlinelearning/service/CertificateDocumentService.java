package com.onlinelearning.service;

import com.onlinelearning.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

/** A self-contained, lossless 216-dpi image PDF: no font downloads or external provider. */
@Service
public class CertificateDocumentService {
 private final String frontendUrl;
 public CertificateDocumentService(@Value("${app.frontend-url:http://localhost:5173}") String frontendUrl){
  this.frontendUrl=frontendUrl.replaceAll("/+$","");
 }
 public String verificationUrl(String code){
  URI base=URI.create(frontendUrl);
  if(!Set.of("http","https").contains(base.getScheme())||base.getHost()==null||base.getRawQuery()!=null||base.getFragment()!=null||base.getUserInfo()!=null)
   throw new IllegalStateException("app.frontend-url must be a public HTTP(S) frontend URL");
  String url=frontendUrl+"/verify-certificate?code="+code;
  if(url.getBytes(StandardCharsets.UTF_8).length>271)throw new IllegalStateException("Frontend URL is too long for certificate QR");
  return url;
 }
 public byte[] pdf(CertificateService.View c){
  if(!"ISSUED".equals(c.status()))throw new ApiException(HttpStatus.CONFLICT,"Chứng nhận đã bị thu hồi, không thể tải PDF.");
  BufferedImage page=render(c);
  try{
   ByteArrayOutputStream image=new ByteArrayOutputStream();
   try(var deflater=new DeflaterOutputStream(image)){
    byte[] row=new byte[page.getWidth()*3];
    for(int y=0;y<page.getHeight();y++){for(int x=0;x<page.getWidth();x++){int rgb=page.getRGB(x,y);row[x*3]=(byte)(rgb>>16);row[x*3+1]=(byte)(rgb>>8);row[x*3+2]=(byte)rgb;}deflater.write(row);}
   }
   byte[] pixels=image.toByteArray(),content="q 842 0 0 595 0 0 cm /Im0 Do Q".getBytes(StandardCharsets.US_ASCII);
   var out=new ByteArrayOutputStream();var offsets=new ArrayList<Integer>();offsets.add(0);
   ascii(out,"%PDF-1.4\n");
   object(out,offsets,1,"<< /Type /Catalog /Pages 2 0 R >>",null);
   object(out,offsets,2,"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",null);
   object(out,offsets,3,"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>",null);
   object(out,offsets,4,"<< /Type /XObject /Subtype /Image /Width "+page.getWidth()+" /Height "+page.getHeight()+" /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "+pixels.length+" >>",pixels);
   object(out,offsets,5,"<< /Length "+content.length+" >>",content);
   int xref=out.size();ascii(out,"xref\n0 6\n0000000000 65535 f \n");
   for(int i=1;i<6;i++)ascii(out,String.format(Locale.ROOT,"%010d 00000 n \n",offsets.get(i)));
   ascii(out,"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n");
   return out.toByteArray();
  }catch(IOException e){throw new IllegalStateException("Cannot generate certificate PDF",e);}
 }
 public BufferedImage render(CertificateService.View c){
  BufferedImage page=new BufferedImage(2526,1785,BufferedImage.TYPE_INT_RGB);
  Graphics2D g=page.createGraphics();
  try{
   g.scale(3,3);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
   g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
   g.setColor(Color.WHITE);g.fillRect(0,0,842,595);
   g.setColor(new Color(239,246,255));g.fillPolygon(new int[]{0,240,0},new int[]{0,0,240},3);
   g.setColor(new Color(248,250,252));g.fillPolygon(new int[]{842,842,610},new int[]{595,363,595},3);
   g.setColor(new Color(37,99,235));g.setStroke(new BasicStroke(2));g.drawRect(25,25,792,545);
   g.setColor(new Color(211,224,244));g.setStroke(new BasicStroke(.7f));g.drawRect(33,33,776,529);
   g.setColor(new Color(37,99,235));g.fillRoundRect(62,54,35,35,9,9);
   g.setFont(font(Font.BOLD,24));g.setColor(Color.WHITE);g.drawString("E",71,80);
   g.setColor(new Color(23,43,77));g.setFont(font(Font.BOLD,19));g.drawString("EduLearn",109,78);
   block(g,c.issuerName(),300,52,475,35,12,2,new Color(70,91,120),false);
   block(g,"CHỨNG NHẬN HOÀN THÀNH",70,124,702,55,31,1,new Color(23,43,77),true);
   block(g,"Trao cho học viên",90,190,662,25,14,1,new Color(100,116,139),false);
   block(g,c.studentName(),90,221,662,63,34,3,new Color(29,78,216),true);
   block(g,"Đã hoàn thành các yêu cầu của khóa học",90,300,662,24,14,1,new Color(100,116,139),false);
   block(g,c.courseTitle(),90,334,662,69,25,3,new Color(23,43,77),true);
   g.setColor(new Color(219,230,246));g.drawLine(70,425,772,425);
   g.setColor(new Color(100,116,139));g.setFont(font(Font.PLAIN,10));g.drawString("NGÀY CẤP",73,450);
   g.setFont(font(Font.BOLD,13));g.setColor(new Color(23,43,77));
   g.drawString(DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(c.issuedAt()),73,469);
   g.setFont(font(Font.PLAIN,9));g.setColor(new Color(100,116,139));g.drawString("MÃ CHỨNG NHẬN",73,493);
   g.setFont(font(Font.BOLD,10));g.setColor(new Color(23,43,77));g.drawString(c.code(),73,511);
   block(g,c.issuerName(),340,449,310,54,14,3,new Color(23,43,77),true);
   block(g,"Đơn vị cấp chứng nhận",340,505,310,18,10,1,new Color(100,116,139),false);
   boolean[][] qr=CertificateQr.encode(verificationUrl(c.code()));
   int module=1,x0=695,y0=443,quiet=4;
   g.setColor(Color.WHITE);g.fillRect(x0,y0,(CertificateQr.SIZE+quiet*2)*module,(CertificateQr.SIZE+quiet*2)*module);
   g.setColor(new Color(15,23,42));
   for(int y=0;y<qr.length;y++)for(int x=0;x<qr[y].length;x++)if(qr[y][x])g.fillRect(x0+(x+quiet)*module,y0+(y+quiet)*module,module,module);
   g.setFont(font(Font.PLAIN,8));g.setColor(new Color(100,116,139));g.drawString("Quét QR để xác minh",689,524);
   block(g,"Chứng nhận ghi nhận hoàn thành các yêu cầu học tập trên hệ thống.",74,539,690,15,9,1,new Color(100,116,139),false);
  }finally{g.dispose();}
  return page;
 }
 private static Font font(int style,int size){return new Font(Font.SANS_SERIF,style,size);}
 private static void block(Graphics2D g,String text,int x,int y,int width,int height,int initialSize,int maxLines,Color color,boolean bold){
  List<String> lines=List.of();int size=initialSize;
  for(;size>=8;size--){
   g.setFont(font(bold?Font.BOLD:Font.PLAIN,size));
   if(g.getFont().canDisplayUpTo(text)!=-1)throw new IllegalStateException("Server font cannot display certificate text");
   lines=wrap(g.getFontMetrics(),text,width);
   if(lines.size()<=maxLines&&lines.size()*g.getFontMetrics().getHeight()<=height)break;
  }
  if(size<8)throw new IllegalArgumentException("Certificate text does not fit");
  g.setColor(color);FontMetrics fm=g.getFontMetrics();
  int baseline=y+(height-lines.size()*fm.getHeight())/2+fm.getAscent();
  for(String line:lines){g.drawString(line,x+(width-fm.stringWidth(line))/2,baseline);baseline+=fm.getHeight();}
 }
 private static List<String> wrap(FontMetrics fm,String text,int width){
  var lines=new ArrayList<String>();StringBuilder line=new StringBuilder();
  for(String word:text.strip().split("\\s+")){
   if(fm.stringWidth(word)>width){
    if(!line.isEmpty()){lines.add(line.toString());line.setLength(0);}
    for(int offset=0;offset<word.length();){int cp=word.codePointAt(offset);String next=new String(Character.toChars(cp));if(fm.stringWidth(line+next)>width&&!line.isEmpty()){lines.add(line.toString());line.setLength(0);}line.append(next);offset+=Character.charCount(cp);}
   }else if(!line.isEmpty()&&fm.stringWidth(line+" "+word)>width){lines.add(line.toString());line.setLength(0);line.append(word);}
   else{if(!line.isEmpty())line.append(' ');line.append(word);}
  }
  if(!line.isEmpty())lines.add(line.toString());return lines;
 }
 private static void object(ByteArrayOutputStream out,List<Integer> offsets,int id,String dictionary,byte[] stream)throws IOException{
  offsets.add(out.size());ascii(out,id+" 0 obj\n"+dictionary+"\n");
  if(stream!=null){ascii(out,"stream\n");out.write(stream);ascii(out,"\nendstream\n");}
  ascii(out,"endobj\n");
 }
 private static void ascii(OutputStream out,String text)throws IOException{out.write(text.getBytes(StandardCharsets.US_ASCII));}
}
