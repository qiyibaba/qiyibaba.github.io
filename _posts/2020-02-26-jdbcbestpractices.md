---
layout:     post                    
title:     	MySQL JDBC使用最佳实践										
subtitle:   讲述jdbc中Loadbalance，prepare，fetchsize的使用
date:       2020-02-26            
author:     Qiyibaba               
header-img: img/post-bg-202002.jpeg   
catalog: true                     
tags:                               
    - java
    - jdbc
---

#### loadbalance的使用

对于分布式数据库来说，中间件通常都是多个，对于这种“多Master”架构，我们通常希望“负载均衡”、“读写分离”等高级特性，这就是Load Balancing协议所能解决的。Load Balancing可以将read/write负载，分布在多个MySQL实例上。

LB协议基于“Failover协议”，即具备Failover特性，其URL格式：

```
jdbc:mysql:loadbalance://[host]:[port],[host]:[port],...[/database]?[property=<value>]&[property=<value>]
```

配置实例url为：

```
url=jdbc:mysql:loadbalance://10.5.7.13:8888,10.5.6.121:8888?characterEncoding=utf8&useSSL=false
```
#### prepare的使用

​	当客户发送一条SQL语句给服务器后，服务器总是需要校验SQL语句的语法格式是否正确，然后把SQL语句编译成可执行的函数，最后才是执行SQL语句。其中校验语法，和编译所花的时间可能比执行SQL语句花的时间还要多。

 	如果我们需要执行多次insert语句，但只是每次插入的值不同，MySQL服务器也是需要每次都去校验SQL语句的语法格式，以及编译，这就浪费了太多的时间。如果使用预编译功能，那么只对SQL语句进行一次语法校验和编译，所以效率要高。

 	从Java中连mysql,使用PrepareStatement的话，默认情况下真正发给服务器端之前已经把?替换了。也就是跟普通的Statement一样：

```java
// 预处理添加数据
ps = conn.prepareStatement("select * from test_dev.emp where empno = ?");
ps.setInt(1, 1);
rs = ps.executeQuery(); 
// 对select语句进行prepare模式设置，实际客户端发给服务端的语句为：select * from test_dev.emp where empno = 1对语句进行了替换。
```

​	为了实现预编译功能，设置参数useCursorFetch=true，再次执行该方法，抓取发送的sql，已经发送带？。

#### fetchsize批量的使用

​	MYSQL默认为从服务器一次取出所有数据放在客户端内存中，fetch size参数不起作用，当一条SQL返回数据量较大时可能会出现JVM OOM。

要一条SQL从服务器读取大量数据，不发生JVM OOM，可以采用以下方法之一：

1. 当statement设置以下属性时，采用的是流数据接收方式，每次只从服务器接收部份数据，直到所有数据处理完毕，不会发生JVM OOM。

   ```java
   setResultSetType(ResultSet.TYPE_FORWARD_ONLY);
   setFetchSize(Integer.MIN_VALUE);
   ```

2. 调用statement的enableStreamingResults方法，实际上enableStreamingResults方法内部封装的就是第1种方式。

3. 设置连接属性useCursorFetch=true (5.0版驱动开始支持)，statement以TYPE_FORWARD_ONLY打开，再设置fetch size参数，表示采用服务器端游标，每次从服务器取fetch_size条数据。

示例，url配置：

```
url=jdbc:mysql://10.47.161.46:9271?characterEncoding=utf8&useCursorFetch=true&useSSL=false
```

 代码示例如下：

```java
public void testFetchSize()
{
	PreparedStatement ps = null;
	Connection conn = null;
	ResultSet rs = null;

	try {
		conn = DBUtils.getConnection();
		// 预处理添加数据
		ps = conn.prepareStatement(SQL);
		// 设置每次预取的条数
		ps.setFetchSize(1);
		rs = ps.executeQuery();

		while (rs.next()) {
			// 输出结果
			...
		}

	} catch (SQLException e) {
		e.printStackTrace();
	} finally {
		// 清理资源
		DBUtils.close(rs,ps,conn);
	}	
}
```
