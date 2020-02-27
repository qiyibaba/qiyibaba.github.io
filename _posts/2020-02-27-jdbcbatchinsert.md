---
layout:     post                    
title:     	JDBC批量模式说明										
subtitle:   如何快速的预置数据，使用缓存，预编译，组合语句等各种条件下怎么选择
date:       2020-02-27           
author:     Qiyibaba               
header-img: img/post-bg-202002.jpg   
catalog: true                     
tags:                               
    - jdbc
    - mysql
---



#### 批量参数和模式

1. rewriteBatchedStatements=true，insert是参数化语句且不是insert ... select 或者 insert... on duplicate key update with an id=last_insert_id(...)的话会执行 executeBatchedInserts，也就是muti value的方式
2. rewriteBatchedStatements=true 语句是都是参数化(没有addbatch(sql)方式加入的)的而且mysql server版本在4.1以上 语句超过三条，则执行executePreparedBatchAsMultiStatement，就是将多个语句通过;分隔一次提交多条sql。比如 "insert into tb1(c1,c2,c3) values (v1,v2,v3);insert into tb1(c1,c2,c3) values (v1,v2,v3)..."
3. 其余的执行executeBatchSerially，也就是还是一条条处理

```java
if (!this.batchHasPlainStatements && this.connection.getRewriteBatchedStatements()) {

    if (canRewriteAsMultiValueInsertAtSqlLevel()) {
        // 执行路径之一，使用batch
        return executeBatchedInserts(batchTimeout);
    }

    if (this.connection.versionMeetsMinimum(4, 1, 0) && !this.batchHasPlainStatements && this.batchedArgs != null
        && this.batchedArgs.size() > 3 /* cost of option setting rt-wise */) {
        // 执行路径之二，版本大于4.1，语句条数大于三条，
        return executePreparedBatchAsMultiStatement(batchTimeout);
    }
}
//执行路径之三，和使用非batch一样
return executeBatchSerially(batchTimeout);
```

#### MySQL实例

##### 使用默认配置语句块执行

```
2020-02-26T06:42:10.252547Z        42 Query     SET autocommit=0
2020-02-26T06:42:10.279657Z        42 Query     select @@session.tx_read_only
2020-02-26T06:42:10.280569Z        42 Query     insert into t1 values (1,'a')
2020-02-26T06:42:10.281292Z        42 Query     insert into t1 values (2,'a')
2020-02-26T06:42:10.288742Z        42 Query     insert into t1 values (3,'a')
2020-02-26T06:42:10.386462Z        42 Query     insert into t1 values (4,'a')
2020-02-26T06:42:10.386965Z        42 Query     insert into t1 values (5,'a')
2020-02-26T06:42:10.387495Z        42 Query     insert into t1 values (6,'a')
2020-02-26T06:42:10.388103Z        42 Query     insert into t1 values (7,'a')
2020-02-26T06:42:10.388562Z        42 Query     insert into t1 values (8,'a')
2020-02-26T06:42:10.389082Z        42 Query     insert into t1 values (9,'a')
2020-02-26T06:42:10.389965Z        42 Query     commit
```

##### 添加rewriteBatchStatements参数

```
2020-02-26T06:43:58.747847Z        45 Query     SET autocommit=0
2020-02-26T06:43:58.777386Z        45 Query     select @@session.tx_read_only
2020-02-26T06:43:58.779948Z        45 Query     insert into t1 values (1,'a'),(2,'a'),(3,'a'),(4,'a'),(5,'a'),(6,'a'),(7,'a'),(8,'a'),(9,'a')
2020-02-26T06:43:58.787636Z        45 Query     commit
```

```
rs = stmt.executeQuery(versionMeetsMinimum(8, 0, 3) || (versionMeetsMinimum(5, 7, 20) && !versionMeetsMinimum(8, 0, 0))
? "select @@session.transaction_read_only" : "select @@session.tx_read_only");
if (rs.next()) {
return rs.getInt(1) != 0; // mysql has a habit of tri+ state booleans
}
```

> 5.7.20以上，包括8版本执行select @@session.transaction_read_only，以下版本执行select @@session.tx_read_only

#### 插入时间测试（仅测试单线程）

如何最快的预置大量数据，前提我们确认commit占整个流程90%的时间，所以commit次数越少，则耗时越短，测试以固定的单次提交条数作为条件，在使用缓存，预编译，组合语句等各种条件下进行测试。

首先预置数据的原则是尽量的使用batch功能，减少提交的次数，编写batch功能代码，模拟数据插入：

```java
public static void mainTest(int totalSize, int batchSize, String url) throws SQLException {
    Connection conn = getConnection(url);
    long start = System.currentTimeMillis();
    try {
        conn.setAutoCommit(false);
        PreparedStatement preparedStatement = conn.prepareStatement("insert into sbtest1(id,k,c,pad) values (?,?,?,?)");
        int cycle = totalSize / batchSize;
        for (int j = 0; j < cycle; j++) {
            for (int i = 1; i <= batchSize; i++) {
                preparedStatement.setInt(1, batchSize * j + i);
                preparedStatement.setInt(2, (int) (Math.random() * 100000000));
                preparedStatement.setString(3, "ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                preparedStatement.setString(4, "ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            conn.commit();
            if (j % 100 == 0) {
                System.out.println("commit " + j + " times,total need " + cycle + " times");
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
    } finally {
        conn.commit();
        conn.close();
    }
    long end = System.currentTimeMillis();
    System.out.println("insert [" + totalSize + "," + batchSize + "] lines total cost " + (end - start) + "ms");
}
```

##### 使本地stmt

用默认参数进行测试，插入120w数据共耗时155.794s：

```
URL:jdbc:mysql://192.168.10.15:5520/lt?useSSL=false
insert [1200000,2000] lines total cost 155794ms
```

##### 使用预编译+缓存

```
URL:jdbc:mysql://192.168.10.15:5520/lt?useSSL=false&useCursorFetch=true&cachePrepStmts=true
insert [1200000,2000] lines total cost 120799ms
```

##### 使本地stmt+rewrite功能

使用组合语句进行插入，耗时仅24.698s

```
URL:jdbc:mysql://192.168.10.15:5520/lt?useSSL=false&rewriteBatchedStatements=true
insert [1200000,2000] lines total cost 24698ms
```

##### 使本地stmt+rewrite功能+本地预编译功能

查看MySQL状态，确实使用预编译

```
mysql> show status like '%Prepared_stmt_count%';
+---------------------------------------------+-------+
| Variable_name                               | Value |
+---------------------------------------------+-------+
| Prepared_stmt_count                         | 2     |
+---------------------------------------------+-------+
7 rows in set (0.00 sec)
```

测试结果如下，耗时28.672s：

```
URL:jdbc:mysql://192.168.10.15:5520/lt?useSSL=false&rewriteBatchedStatements=true&useCursorFetch=true
insert [1200000,2000] lines total cost 25509ms
```

##### 使用rewrite功能+预编译+缓存

```
URL:jdbc:mysql://192.168.10.15:5520/lt?useSSL=false&rewriteBatchedStatements=true&useCursorFetch=true&cachePrepStmts=true
insert [1200000,2000] lines total cost 25562ms
```

经过测试，缓存在批量插入的过程中，并未表现出明显的性能提升，性能可能还不如批量的效果。

