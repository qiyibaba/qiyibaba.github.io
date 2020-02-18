---
layout:     post                    # 使用的布局（不需要改）
title:      JAVA故障定位基础教程异常篇  # 标题 
subtitle:   本文的定位故障定位的基础教程之异常篇，是指导在应对Java业务问题的异常日志时，能够掌握异常日志的内容含义，已经掌握常用的问题定位流程，指导问题分析。本文示例仅作为对流程的理解使用，其他有关数据库连接池的具体配置及使用问题可以查阅知识库，不在本文的涵盖的范围之内。 #副标题
date:       2020-02-18              # 时间
author:     Qiyibaba                # 作者
header-img: img/post-bg-2015.jpg    #这篇文章标题背景图片
catalog: true                       # 是否归档
tags:                               #标签
    - java
    - exception
---



## 异常和堆栈

​	JDK提供一系列标准的运行时异常接口，程序在运行时选择捕获并抛出。开发者需要面对异常抛出的堆栈信息进行分析。

### 异常堆栈

```
java.sql.SQLException: Access denied for user 'root'@'localhost' (using password: YES)	at com.mysql.jdbc.SQLError.createSQLException(SQLError.java:998)	at com.mysql.jdbc.MysqlIO.checkErrorPacket(MysqlIO.java:3847)	at com.mysql.jdbc.MysqlIO.checkErrorPacket(MysqlIO.java:3783)	at com.mysql.jdbc.MysqlIO.checkErrorPacket(MysqlIO.java:871)	at com.mysql.jdbc.MysqlIO.proceedHandshakeWithPluggableAuthentication(MysqlIO.java:1665)	at com.mysql.jdbc.MysqlIO.doHandshake(MysqlIO.java:1207)	at com.mysql.jdbc.ConnectionImpl.coreConnect(ConnectionImpl.java:2249)	at com.mysql.jdbc.ConnectionImpl.connectOneTryOnly(ConnectionImpl.java:2280)	at com.mysql.jdbc.ConnectionImpl.createNewIO(ConnectionImpl.java:2079)	at com.mysql.jdbc.ConnectionImpl.<init>(ConnectionImpl.java:794)	at com.mysql.jdbc.JDBC4Connection.<init>(JDBC4Connection.java:44)	at sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)	at sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)	at sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)	at java.lang.reflect.Constructor.newInstance(Constructor.java:423)	at com.mysql.jdbc.Util.handleNewInstance(Util.java:400)	at com.mysql.jdbc.ConnectionImpl.getInstance(ConnectionImpl.java:399)	at com.mysql.jdbc.NonRegisteringDriver.connect(NonRegisteringDriver.java:325)	at java.sql.DriverManager.getConnection(DriverManager.java:664)	at java.sql.DriverManager.getConnection(DriverManager.java:247)	at com.qiyibaba.jdbc.JdbcTest.main(JdbcTest.java:23)
```

​	Java的方法在执行的时候是在虚拟机栈中执行的，每执行一个方法就会新建一个栈帧然后压入到虚拟机栈中。这是一个后进先出的结构，所以报错的时候也是从被调用者最开始报错，然后调用者依次报错，所以打印错误时的顺序也是报错的位置在最上面，调用者依次向后排。由此我们可以得出结论：上面报错，下面跟随。

​	从上面的分析我们知道报错位置在上面是一个SQLException，异常抛出的位置是“com.mysql.jdbc.SQLError.createSQLException(SQLError.java:998)”即该异常由类SQLError.java类的第998行所抛出的，该类属于JDBC驱动，不是我们自己的代码，那是因为我们的代码调用了一些第三方jar包的代码，如本例中的JDBC驱动包。但是这并不影响我们去定位问题，我们还是根据上面报错去找到实际的报错,报错信息是“Access denied for user 'root'@'localhost' (using password: YES)”，登陆失败。

​	那如何找到我们的代码呢？下面介绍如何从堆栈中找到具体自己的代码和方法实现。

### 从堆栈到源码

​	首先简单的方法是通过包，这也是在异常堆栈中最常用的使用方法，如上例中的异常堆栈中，“com.mysql.jdbc”这个是JDBC驱动的包路径，“com.qiyibaba.jdbc”则是我们自己代码的路径，此时我们只需要grep这个堆栈，即可以获取我们代码的执行信息，如本例中只有一行“com.qiyibaba.jdbc.JdbcTest.main(JdbcTest.java:23)”这行就是我们代码中出现异常的起始位置了。

​	堆栈信息能完整展示整个代码执行流的执行过程，如JdbcTest.java:23的main()调用了DriverManager.java:247行的getConnection(),该方法又调用的同一个文件664行的getConnection()，依次执行如下。

​	如果在有jar包的情况下可以直接反编译jar包，在不知道类属于哪个jar包的时候，可以直接搜索lib库找到类属于哪个jar包：

```
[bugmgr@db02 lib]$ grep "SQLError" *
Binary file mysql-connector-java-5.1.29.jar matches
Binary file spring-jdbc-4.0.2.RELEASE.jar matches
Binary file xalan-2.7.0.jar matches
```

​	如果识别出多个，可以添加包作为条件进一步过滤：

```
[bugmgr@db02 lib]$ grep "jdbc/SQLError" *
Binary file mysql-connector-java-5.1.29.jar matches
```

​	找到jar包后，我们可以借助jd-gui工具进行反编译，如本例中我们知道最终抛出异常的是MySQL的驱动包，我们直接拿出该jar包进行反编译，如下图将jar包通过jd-gui进行打开。

**注意：有时反编译的行号跟实际的行号会有偏差，需要配合实际的方法名进行配合确认。**

## 与数据库有关的异常

​	我们将从JDK,JDBC,DataSource三块内容来认识和理解异常。

### JDK异常

​	SQLException是JDK提供的操作数据库异常的通用接口，当使用 JDBC 与数据库进行交互遇见错误的时候，将会抛出名为 SQLException 的异常。如果不强制捕获SQLException的话，几乎无法使用JDBC做任何事情。SQLException表示在尝试访问数据库的时候出现了问题。SQLException标准构造函数如下：

```java
public SQLException(String reason, String SQLState, int vendorCode) {
    super(reason);
    this.SQLState = SQLState;
    this.vendorCode = vendorCode;
    if (!(this instanceof SQLWarning)) {
        if (DriverManager.getLogWriter() != null) {
            DriverManager.println("SQLState(" + SQLState + ") vendor code(" + vendorCode + ")");
            printStackTrace(DriverManager.getLogWriter());
        }
    }
}
```

​	一个标准异常至少包括三个部分：reason（错误描述），SQLState（XOPEN SQLstate约定或SQL:2003约定的值），vendorCode（错误码）。SQLException一般在如下场景中抛出：

1. 应用程序无法连接数据库；
2. 要执行的查询存在语法错误；
3. 查询中所使用的表和/或列不存在；
4. 试图插入或更新的数据违反了数据库约束；

下面代码执行会演示一个无法连接数据库的异常堆栈：

```java
Connection conn = null;
try {
    // 注册 JDBC 驱动
    Class.forName("com.mysql.jdbc.Driver");
    // 打开链接，密码是zte，修改配置密码为zte1
    conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/lt", "root", "zte1");
} catch (SQLException e) {
    // 处理 JDBC 错误
}
```

​	执行代码，得到如下结果：

```
java.sql.SQLException: Access denied for user 'root'@'localhost' (using password: YES)	at com.mysql.jdbc.SQLError.createSQLException(SQLError.java:998)	at com.mysql.jdbc.MysqlIO.checkErrorPacket(MysqlIO.java:3847)	at com.mysql.jdbc.MysqlIO.checkErrorPacket(MysqlIO.java:3783)	at com.mysql.jdbc.MysqlIO.checkErrorPacket(MysqlIO.java:871)	at com.mysql.jdbc.MysqlIO.proceedHandshakeWithPluggableAuthentication(MysqlIO.java:1665)	at com.mysql.jdbc.MysqlIO.doHandshake(MysqlIO.java:1207)	at com.mysql.jdbc.ConnectionImpl.coreConnect(ConnectionImpl.java:2249)	at com.mysql.jdbc.ConnectionImpl.connectOneTryOnly(ConnectionImpl.java:2280)	at com.mysql.jdbc.ConnectionImpl.createNewIO(ConnectionImpl.java:2079)	at com.mysql.jdbc.ConnectionImpl.<init>(ConnectionImpl.java:794)	at com.mysql.jdbc.JDBC4Connection.<init>(JDBC4Connection.java:44)	at sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)	at sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)	at sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)	at java.lang.reflect.Constructor.newInstance(Constructor.java:423)	at com.mysql.jdbc.Util.handleNewInstance(Util.java:400)	at com.mysql.jdbc.ConnectionImpl.getInstance(ConnectionImpl.java:399)	at com.mysql.jdbc.NonRegisteringDriver.connect(NonRegisteringDriver.java:325)	at java.sql.DriverManager.getConnection(DriverManager.java:664)	at java.sql.DriverManager.getConnection(DriverManager.java:247)	at com.qiyibaba.jdbc.JdbcTest.main(JdbcTest.java:23)
```

​	该异常具体堆栈第二部分已经解析过，不再赘述。只要知道SQLException最为数据库异常最基础的异常类，很多时候，不管是JDBC驱动还是连接池，都会在默认情况下选择用标准的SQLException进行异常抛出。

### JDBC驱动异常

​	MySQL使用MySQL Connector/J（一个实现Java数据库连接（JDBC）API的驱动程序）为用Java编程语言开发的客户机应用程序提供连接。MySQL Connector/J是一个JDBC4驱动程序。可以使用与JDBC3.0和JDBC4.x规范兼容的不同版本。类型4表示驱动程序是MySQL协议的纯Java实现，不依赖MySQL客户端库。

​	对于使用通用数据访问设计模式的大型程序，通常还会配合流行的持久性框架（如Hibernate、Spring的JDBC模板或MyBatis SQL映射）来减少JDBC代码的数量，以便进行调试、优化、保护和维护。此处不做讨论。

​	下面演示一个驱动抛出异常的案例，其实案例1也是驱动抛出的异常，只是该异常使用的是JDK默认的SQLException抛出的。

```
com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException: Unknown database 'lt'	at sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)	at sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)	at sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)	at java.lang.reflect.Constructor.newInstance(Constructor.java:423)	at com.mysql.jdbc.Util.handleNewInstance(Util.java:400)	at com.mysql.jdbc.Util.getInstance(Util.java:383)	at com.mysql.jdbc.SQLError.createSQLException(SQLError.java:980)	at com.mysql.jdbc.MysqlIO.checkErrorPacket(MysqlIO.java:3847)	at com.mysql.jdbc.MysqlIO.checkErrorPacket(MysqlIO.java:3783)	at com.mysql.jdbc.MysqlIO.checkErrorPacket(MysqlIO.java:871)	at com.mysql.jdbc.MysqlIO.proceedHandshakeWithPluggableAuthentication(MysqlIO.java:1665)	at com.mysql.jdbc.MysqlIO.doHandshake(MysqlIO.java:1207)	at com.mysql.jdbc.ConnectionImpl.coreConnect(ConnectionImpl.java:2249)	at com.mysql.jdbc.ConnectionImpl.connectOneTryOnly(ConnectionImpl.java:2280)	at com.mysql.jdbc.ConnectionImpl.createNewIO(ConnectionImpl.java:2079)	at com.mysql.jdbc.ConnectionImpl.<init>(ConnectionImpl.java:794)	at com.mysql.jdbc.JDBC4Connection.<init>(JDBC4Connection.java:44)	at sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)	at sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)	at sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)	at java.lang.reflect.Constructor.newInstance(Constructor.java:423)	at com.mysql.jdbc.Util.handleNewInstance(Util.java:400)	at com.mysql.jdbc.ConnectionImpl.getInstance(ConnectionImpl.java:399)	at com.mysql.jdbc.NonRegisteringDriver.connect(NonRegisteringDriver.java:325)	at java.sql.DriverManager.getConnection(DriverManager.java:664)	at java.sql.DriverManager.getConnection(DriverManager.java:247)	at com.qiyibaba.jdbc.JdbcTest.main(JdbcTest.java:23)
```

​	我们现在知道抛出的异常是com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException，我们可以从几个方面确认该异常是与数据库相关的异常。

1. 看包，包是“com.mysql.jdbc”，这个JDBC驱动的包路径
2. 看异常名“MySQLSyntaxErrorException”，根据字面意思就是MySQL语法错误的异常。
3. 看类方法实现，我们通过找到该类的定义“public class MySQLSyntaxErrorException extends SQLSyntaxErrorException”，该类是SQLSyntaxErrorException的子类，再看SQLSyntaxErrorException类“public class SQLSyntaxErrorException extends SQLNonTransientException”,该类是SQLNonTransientException的子类，再往上看“public class SQLNonTransientException extends java.sql.SQLException”，即MySQLSyntaxErrorException是SQLException的子类，而SQLException就是数据库异常的基类，确认该异常是与数据库相关。如果你是通过IDE的话，则可以查看继承树就一目了然了。

​	我们会想要在捕获一个异常后返回另一个异常，并且希望将原来异常的信息保存下来。这被称作异常链。通过jdk返回的异常接口均是SQLException，当驱动需要返回自定义的详细异常的时候，这是就采用异常链的手段。该例中实例是驱动抓取了服务端返回的异常信息之后，经过自己的解析细化，返回具体的异常，其本质上还是一个SQLException。如下代码所示：

```java
if (sqlState.startsWith("42")) {
    if (!Util.isJdbc4()) {
        sqlEx = new MySQLSyntaxErrorException(message, sqlState, vendorErrorCode);
    } else {
        sqlEx = (SQLException) Util.getInstance("com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException", new Class[] { String.class,
                String.class, Integer.TYPE }, new Object[] { message, sqlState, Integer.valueOf(vendorErrorCode) }, interceptor);
    }
```

### DataSource异常

​	对于大型应用而言，需要频繁的建立、关闭数据库连接，会极大的减低系统的性能，这时候使用数据库连接池。如何确认数据库连接池的错误，下面演示一个Druid连接池的错误来分析。

```
Caused by: com.alibaba.druid.pool.GetConnectionTimeoutException: wait millis 60365, active 5, maxActive 5
    at com.alibaba.druid.pool.DruidDataSource.getConnectionInternal(DruidDataSource.java:1137)
    at com.alibaba.druid.pool.DruidDataSource.getConnectionDirect(DruidDataSource.java:953)
    at com.alibaba.druid.pool.DruidDataSource.getConnection(DruidDataSource.java:933)
    at com.alibaba.druid.pool.DruidDataSource.getConnection(DruidDataSource.java:923)
    at com.alibaba.druid.pool.DruidDataSource.getConnection(DruidDataSource.java:100)
    at org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl.getConnection(DatasourceConnectionProviderImpl.java:122)
    at org.hibernate.internal.AbstractSessionImpl$NonContextualJdbcConnectionAccess.obtainConnection(AbstractSessionImpl.java:386)
    at org.hibernate.resource.jdbc.internal.LogicalConnectionManagedImpl.acquireConnectionIfNeeded(LogicalConnectionManagedImpl.java:87)
    … 100 common frames omitted
```

​	同样的，可以从类名中获取,包路径是“com.alibaba.druid.pool”，很容易就识别出这就是Druid抛出的异常。如果在还不清楚的情况下同样也可以识别异常类的定义,又是继承自SQLException的子类：

```java
public class GetConnectionTimeoutException extends SQLException {
    private static final long serialVersionUID = 1L;

    public GetConnectionTimeoutException(String reason) {
        super(reason);
    }

    public GetConnectionTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

​	说明：上例所示是一个连接池的报错，报错信息是需要获取一个连接，但是获取连接超时，超时原因是连接池最大连接就是5，当前已经使用了5个连接，无法释放连接使用，所以超时失败了，在这种情况下要做的就是扩大连接池的大小。连接池的大小配置很多需要跟实际的业务并发有关。

## 总结

1. 与数据库有关的异常均是SQLException或SQLException的子类
2. 异常堆栈是自下而上的引用顺序
3. 可以使用jd-gui反编译jar包获取源码
