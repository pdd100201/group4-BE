package com.onlinelearning.service;

import java.nio.charset.StandardCharsets;
import java.util.*;
/**
 * Byte-mode QR, version 10 / error correction L / mask 0.
 * Fixed capacity keeps certificate URL rendering deterministic and dependency-free.
 */
public final class CertificateQr {
 public static final int SIZE=57;
 private CertificateQr() {}
 public static boolean[][] encode(String text){
  byte[] payload=text.getBytes(StandardCharsets.UTF_8);
  if(payload.length>271)throw new IllegalArgumentException("Certificate verification URL exceeds QR capacity");
  List<Boolean> bits=new ArrayList<>();append(bits,4,4);append(bits,payload.length,16);
  for(byte b:payload)append(bits,b&255,8);
  append(bits,0,Math.min(4,274*8-bits.size()));
  while(bits.size()%8!=0)bits.add(false);
  byte[] data=new byte[274];
  int populated=bits.size()/8;
  for(int i=0;i<bits.size();i++)if(bits.get(i))data[i/8]|=(byte)(1<<(7-i%8));
  for(int i=populated;i<data.length;i++)data[i]=(byte)((i-populated)%2==0?0xEC:0x11);
  byte[][] blocks=new byte[4][],parity=new byte[4][];int offset=0;
  byte[] divisor=divisor(18);
  for(int i=0;i<4;i++){int length=i<2?68:69;blocks[i]=Arrays.copyOfRange(data,offset,offset+length);offset+=length;parity[i]=remainder(blocks[i],divisor);}
  byte[] words=new byte[346];int pos=0;
  for(int i=0;i<69;i++)for(int b=0;b<4;b++)if(i<blocks[b].length)words[pos++]=blocks[b][i];
  for(int i=0;i<18;i++)for(int b=0;b<4;b++)words[pos++]=parity[b][i];
  boolean[][] matrix=new boolean[SIZE][SIZE],function=new boolean[SIZE][SIZE];
  for(int i=0;i<SIZE;i++){set(matrix,function,6,i,i%2==0);set(matrix,function,i,6,i%2==0);}
  finder(matrix,function,3,3);finder(matrix,function,SIZE-4,3);finder(matrix,function,3,SIZE-4);
  int[] centers={6,28,50};
  for(int y:centers)for(int x:centers)if(!((x==6&&y==6)||(x==6&&y==50)||(x==50&&y==6))){
   for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++)set(matrix,function,x+dx,y+dy,Math.max(Math.abs(dx),Math.abs(dy))!=1);
  }
  int format=0x77C4; // BCH-protected format bits for L, mask 0.
  for(int i=0;i<=5;i++)set(matrix,function,8,i,bit(format,i));
  set(matrix,function,8,7,bit(format,6));set(matrix,function,8,8,bit(format,7));set(matrix,function,7,8,bit(format,8));
  for(int i=9;i<15;i++)set(matrix,function,14-i,8,bit(format,i));
  for(int i=0;i<8;i++)set(matrix,function,SIZE-1-i,8,bit(format,i));
  for(int i=8;i<15;i++)set(matrix,function,8,SIZE-15+i,bit(format,i));
  set(matrix,function,8,SIZE-8,true);
  int version=10,remainder=version;
  for(int i=0;i<12;i++)remainder=(remainder<<1)^((remainder>>>11)*0x1F25);
  int versionBits=(version<<12)|remainder;
  for(int i=0;i<18;i++){int a=SIZE-11+i%3,b=i/3;set(matrix,function,a,b,bit(versionBits,i));set(matrix,function,b,a,bit(versionBits,i));}
  int index=0;
  for(int right=SIZE-1;right>=1;right-=2){
   if(right==6)right=5;
   for(int vert=0;vert<SIZE;vert++)for(int j=0;j<2;j++){
    int x=right-j,y=((right+1)&2)==0?SIZE-1-vert:vert;
    if(!function[y][x]){matrix[y][x]=index<words.length*8&&bit(words[index/8]&255,7-index%8);index++;}
   }
  }
  if(index!=words.length*8)throw new IllegalStateException("Invalid QR function pattern allocation: "+index);
  for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(!function[y][x]&&(x+y)%2==0)matrix[y][x]=!matrix[y][x];
  return matrix;
 }
 private static void append(List<Boolean> bits,int value,int length){for(int i=length-1;i>=0;i--)bits.add(bit(value,i));}
 private static boolean bit(int value,int index){return ((value>>>index)&1)!=0;}
 private static void set(boolean[][]m,boolean[][]f,int x,int y,boolean value){if(x>=0&&y>=0&&x<SIZE&&y<SIZE){m[y][x]=value;f[y][x]=true;}}
 private static void finder(boolean[][]m,boolean[][]f,int x,int y){for(int dy=-4;dy<=4;dy++)for(int dx=-4;dx<=4;dx++){int distance=Math.max(Math.abs(dx),Math.abs(dy));set(m,f,x+dx,y+dy,distance!=2&&distance!=4);}}
 private static int multiply(int x,int y){int result=0;for(int i=7;i>=0;i--){result=(result<<1)^((result>>>7)*0x11D);result^=((y>>>i)&1)*x;}return result;}
 private static byte[] divisor(int degree){
  byte[] result=new byte[degree];result[degree-1]=1;int root=1;
  for(int i=0;i<degree;i++){for(int j=0;j<degree;j++){result[j]=(byte)multiply(result[j]&255,root);if(j+1<degree)result[j]^=result[j+1];}root=multiply(root,2);}
  return result;
 }
 private static byte[] remainder(byte[] data,byte[] divisor){
  byte[] result=new byte[divisor.length];
  for(byte value:data){int factor=(value^result[0])&255;System.arraycopy(result,1,result,0,result.length-1);result[result.length-1]=0;for(int i=0;i<result.length;i++)result[i]^=(byte)multiply(divisor[i]&255,factor);}
  return result;
 }
}