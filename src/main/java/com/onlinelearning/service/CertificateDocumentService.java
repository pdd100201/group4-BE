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
   Color ink=new Color(36,42,48), muted=new Color(83,89,96), rule=new Color(153,160,165);
   g.setColor(new Color(255,254,251));g.fillRect(0,0,842,595);
   g.clipRect(12,12,818,571);
   guilloche(g,174,496,245);
   guilloche(g,671,300,218);
   g.setClip(null);
   g.setColor(rule);g.setStroke(new BasicStroke(.75f));g.drawRect(12,12,818,571);
   g.setColor(new Color(222,221,214));g.drawRect(16,16,810,563);
   for(int x:new int[]{6,832})for(int y:new int[]{6,585}){
    g.setColor(rule);g.drawRect(x,y,4,4);
   }

   Polygon ribbon=new Polygon(new int[]{560,757,757,658,560},new int[]{12,12,377,451,377},5);
   g.setPaint(new GradientPaint(560,0,new Color(239,241,244),757,0,new Color(220,226,233)));
   g.fill(ribbon);
   g.setClip(ribbon);
   g.setColor(new Color(139,151,166,24));g.setStroke(new BasicStroke(.35f));
   for(int x=563;x<757;x+=4)g.drawLine(x,12,x,451);
   g.setClip(null);
   g.setColor(rule);g.setStroke(new BasicStroke(.75f));g.draw(ribbon);
   block(g,"CHỨNG CHỈ",570,66,177,30,20,1,ink,false,true);
   block(g,"HOÀN THÀNH KHÓA HỌC",570,99,177,36,12,2,ink,false,true);
   g.setColor(new Color(94,106,120));g.setStroke(new BasicStroke(1));
   g.drawOval(591,213,134,134);g.drawOval(596,218,124,124);g.drawOval(610,232,96,96);
   for(int i=0;i<72;i++){
    double angle=i*Math.PI/36;
    g.fillOval(657+(int)Math.round(71*Math.cos(angle)),279+(int)Math.round(71*Math.sin(angle)),3,3);
   }
   block(g,"EDULEARN",608,253,100,34,16,1,ink,true,true);
   block(g,"CHỨNG NHẬN",608,286,100,24,10,1,muted,false,true);

   block(g,"EduLearn",72,58,450,43,31,1,new Color(35,78,159),true,false);
   String issued=DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(c.issuedAt());
   block(g,"Ngày cấp "+issued,72,161,450,24,11,1,muted,false,false);
   block(g,c.studentName(),72,192,450,76,33,4,ink,true,false);
   block(g,"Đã hoàn thành khóa học",72,277,450,21,13,1,muted,false,false);
   block(g,c.courseTitle(),72,306,450,78,22,4,ink,true,false);
   g.setColor(rule);g.drawLine(72,444,280,444);
   block(g,c.issuerName(),72,451,443,54,17,3,ink,true,false);
   block(g,"Đơn vị cấp chứng chỉ",72,507,443,20,11,1,muted,false,false);
   boolean[][] qr=CertificateQr.encode(verificationUrl(c.code()));
   int module=1,x0=626,y0=472,quiet=4;
   g.setColor(Color.WHITE);g.fillRect(x0,y0,(CertificateQr.SIZE+quiet*2)*module,(CertificateQr.SIZE+quiet*2)*module);
   g.setColor(new Color(15,23,42));
   for(int y=0;y<qr.length;y++)for(int x=0;x<qr[y].length;x++)if(qr[y][x])g.fillRect(x0+(x+quiet)*module,y0+(y+quiet)*module,module,module);
   block(g,"Quét QR để xác minh",578,539,160,15,9,1,muted,false,true);
   block(g,c.code(),578,554,160,15,8,1,ink,false,true);
  }finally{g.dispose();}
  return page;
 }
 private static void guilloche(Graphics2D g,int cx,int cy,int radius){
  g.setColor(new Color(180,139,70,30));
  g.setStroke(new BasicStroke(.55f));
  for(int ring=0;ring<25;ring++){
   var path=new java.awt.geom.Path2D.Double();
   for(int i=0;i<=360;i++){
    double angle=i*Math.PI/180, phase=ring*.15;
    double distance=radius-ring*3.6+14*Math.cos(9*angle+phase)+5*Math.sin(17*angle-phase);
    double x=cx+distance*Math.cos(angle),y=cy+distance*Math.sin(angle);
    if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
   }
   path.closePath();g.draw(path);
  }
 }
 private static Font font(int style,int size){return new Font(Font.SERIF,style,size);}
 private static void block(Graphics2D g,String text,int x,int y,int width,int height,int initialSize,int maxLines,Color color,boolean bold,boolean centered){
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
  for(String line:lines){g.drawString(line,centered?x+(width-fm.stringWidth(line))/2:x,baseline);baseline+=fm.getHeight();}
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
