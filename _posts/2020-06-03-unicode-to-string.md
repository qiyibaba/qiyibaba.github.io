---
layout:     post                    
title:     	 unicode和string转换										
subtitle:   unicode和string转换
date:       2020-06-03           
author:     Qiyibaba               
header-img: img/post-bg-202003.jpg   
catalog: true                     
tags:                               
    - java
    - unicode
---
```java
public class UnicodeExchange {
    public static void main(String args[]){
        String s = "u6ca1u6709u90a3u4e2au6587u4ef6u6216u76eeu5f55";
        System.out.println(s + "==>" + unicodeToString_2(s));
    }

    public static String unicodeToString(String unicode) {
        if (unicode == null || "".equals(unicode)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = -1;
        int pos = 0;
        while ((i = unicode.indexOf("\\u", pos)) != -1) {
            sb.append(unicode.substring(pos, i));
            if (i + 5 < unicode.length()) {
                pos = i + 6;
                sb.append((char) Integer.parseInt(unicode.substring(i + 2, i + 6), 16));
            }
        }
        return sb.toString();
    }

    public static String unicodeToString_2(String unicode) {
        if (unicode == null || "".equals(unicode)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = -1;
        int pos = 0;
        while ((i = unicode.indexOf("u", pos)) != -1) {
            sb.append(unicode.substring(pos, i));
            if (i + 4 < unicode.length()) {
                pos = i + 5;
                sb.append((char) Integer.parseInt(unicode.substring(i + 1, i + 5), 16));
            }
        }
        return sb.toString();
    }


    //编码
    public static String stringToUnicode(String string) {
        if (string == null || "".equals(string)) {
            return null;
        }
        StringBuffer unicode = new StringBuffer();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            unicode.append("\\u" + Integer.toHexString(c));
        }
        return unicode.toString();
    }
}

```


