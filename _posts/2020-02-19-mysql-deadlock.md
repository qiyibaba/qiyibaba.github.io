---
layout:     post                    
title:      MySQL死锁日志分析 
subtitle:   死锁一般是事务相互等待对方资源，最后形成环路造成的。如何查看死锁日志，又如何分析死锁。
date:       2020-02-19             
author:     Qiyibaba               
header-img: img/post-bg-202002.jpeg   
catalog: true                     
tags:                               
    - mysql
    - deadlock
---

### 初始化数据

```mysql
mysql> CREATE TABLE `test` (
    -> `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
    -> `a` int(11) unsigned DEFAULT NULL,
    -> PRIMARY KEY (`id`),
    -> UNIQUE KEY `a` (`a`)
    -> );

Query OK, 0 rows affected (10.17 sec)

mysql>
mysql> insert into test(a) values (1),(2),(3),(4);
Query OK, 4 rows affected (0.01 sec)
Records: 4  Duplicates: 0  Warnings: 0

mysql> select * from test;
+----+------+
| id | a    |
+----+------+
|  1 |    1 |
|  2 |    2 |
|  3 |    3 |
|  4 |    4 |
+----+------+
4 rows in set (0.00 sec)
```

### 模拟死锁产生过程

| step | session 1                                                    | session 2                                |
| ---- | ------------------------------------------------------------ | ---------------------------------------- |
| 1    |                                                              | begin;                                   |
| 2    |                                                              | delete from test where a = 2;            |
| 3    | begin;                                                       |                                          |
| 4    | delete from test where a = 2;                                |                                          |
| 5    |                                                              | insert into test (id, a) values (10, 2); |
| 6    | ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction |                                          |
| 7    |                                                              | rollback;                                |

### show engine innodb status查看死锁信息

```mysql
------------------------
LATEST DETECTED DEADLOCK
------------------------
2020-02-19 10:31:53 0x7fa53bdac700
*** (1) TRANSACTION:
TRANSACTION 135844382, ACTIVE 15 sec starting index read
mysql tables in use 1, locked 1
LOCK WAIT 2 lock struct(s), heap size 1136, 1 row lock(s)
MySQL thread id 4, OS thread handle 140347649992448, query id 21 localhost root updating
delete from test where a = 2
*** (1) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 122 page no 4 n bits 80 index a of table `test`.`test` trx id 135844382 (lock_mode X)() locks rec but not gap waiting
Record lock, heap no 6 PHYSICAL RECORD: n_fields 2; compact format; info bits 32
 0: len 4; hex 00000002; asc     ;;
 1: len 4; hex 00000002; asc     ;;

-- TRANSACTION 1
-- SQL：delete from test where a = 2
-- WAITING:index a of table `test`.`test`(lock_mode X)(RECORD LOCKS)
-- lock_mode X locks rec but not gap waiting
从日志里我们可以看到事务 1 当前正在执行 delete from test where a = 2，该条语句正在申请索引 a
的 X 锁，所以提示 lock_mode X waiting。

*** (2) TRANSACTION:
TRANSACTION 135844377, ACTIVE 32 sec inserting
mysql tables in use 1, locked 1
4 lock struct(s), heap size 1136, 3 row lock(s), undo log entries 2
MySQL thread id 3, OS thread handle 140347650524928, query id 22 localhost root update
insert into test (id, a) values (10,2)
*** (2) HOLDS THE LOCK(S):
RECORD LOCKS space id 122 page no 4 n bits 80 index a of table `test`.`test` trx id 135844377 lock_mode X locks rec but not gap
Record lock, heap no 6 PHYSICAL RECORD: n_fields 2; compact format; info bits 32
 0: len 4; hex 00000002; asc     ;;
 1: len 4; hex 00000002; asc     ;;
*** (2) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 122 page no 4 n bits 80 index a of table `test`.`test` trx id 135844377 lock mode S waiting
Record lock, heap no 6 PHYSICAL RECORD: n_fields 2; compact format; info bits 32
 0: len 4; hex 00000002; asc     ;;
 1: len 4; hex 00000002; asc     ;;
 
-- TRANSACTION 2
-- SQL：insert into test (id, a) values (10,2)
-- HOLDS：index a of table `test`.`test`(lock_mode X locks)(RECORD LOCKS)
-- WAITING FOR:index a of table `test`.`test`(lock mode S)(RECORD LOCKS)
-- 注释：为什么申请S锁：因为 a 字段是一个唯一索引，所以 insert 语句会在插入前进行一次 duplicate key 的检查，为了使这次检查成功，需要申请 S 锁防止其他事务对 a字段进行修改
-- 那么为什么该 S 锁会失败呢？这是对同一个字段的锁的申请是需要排队的。S 锁前面还有一个未申请成功的 X 锁，所以 S 锁必须等待，所以形成了循环等待，死锁出现了。通过阅读死锁日志，我们可以清楚地知道两个事务形成了怎样的循环等待，再加以分析，就可以逆向推断出循环等待的成因，也就是死锁形成的原因。

*** WE ROLL BACK TRANSACTION (1)
-- 最后一步回滚
```

### 死锁产生添加注解

| step | session 1                                                    | session 2                                                    |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 1    |                                                              | begin;                                                       |
| 2    |                                                              | delete from test where a = 2;<br>index a of table `test`.`test`(lock_mode X locks)(RECORD LOCKS) |
| 3    | begin;                                                       |                                                              |
| 4    | delete from test where a = 2;<br>WAITING:index a of table `test`.`test`(lock_mode X)(RECORD LOCKS) |                                                              |
| 5    |                                                              | insert into test (id, a) values (10, 2);<br>WAITING FOR:index a of table `test`.`test`(lock mode S)(RECORD LOCKS) |
| 6    | ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction |                                                              |
| 7    |                                                              | rollback;                                                    |

### 日志打印死锁信息

添加配置innodb_print_all_deadlocks=1将死锁打印至日志中

```
mysql> set global innodb_print_all_deadlocks=on;
Query OK, 0 rows affected (0.00 sec)
```

在mysql日志中查看死锁信息,死锁信息同engine innodb中信息：

```
2020-02-19T03:18:57.449242Z 7 [Note] InnoDB: Transactions deadlock detected, dumping detailed information.
2020-02-19T03:18:57.449285Z 7 [Note] InnoDB:
*** (1) TRANSACTION:

TRANSACTION 135844389, ACTIVE 11 sec starting index read
mysql tables in use 1, locked 1
LOCK WAIT 2 lock struct(s), heap size 1136, 1 row lock(s)
MySQL thread id 3, OS thread handle 140347650524928, query id 45 localhost root updating
......
```

### （附）如何查询锁等待的信息

```mysql
mysql> SELECT r.trx_id AS waiting_trx_id, r.trx_mysql_thread_id AS waiting_thread, TIMESTAMPDIFF(SECOND, r.trx_wait_started, CURRENT_TIMESTAMP) AS wait_time, r.trx_query AS waiting_query, l.lock_table AS waiting_table_lock, b.trx_id AS blocking_trx_id, b.trx_mysql_thread_id AS blocking_thread, SUBSTRING(p.host,1,INSTR(p.host, ':') -1 ) AS blocking_host, SUBSTRING(p.host, INSTR(p.host, ':') +1 ) AS block_port, IF(p.command="Sleep",p.time,0) AS idle_in_trx, b.trx_query AS blcoking_query from information_schema.innodb_lock_waits AS w INNER JOIN information_schema.innodb_trx AS b ON b.trx_id=w.blocking_trx_id INNER JOIN information_schema.innodb_trx AS r ON r.trx_id = w.requesting_trx_id INNER JOIN information_schema.innodb_locks AS l ON w.requested_lock_id = l.lock_id LEFT JOIN information_schema.processlist AS p ON p.id = b.trx_mysql_thread_id ORDER BY wait_time DESC\G;
*************************** 1. row ***************************
    waiting_trx_id: 135844396
    waiting_thread: 8 --正在等待的线程
         wait_time: 6 --已经等待时间
     waiting_query: delete from test where a = 2 --等待执行的语句
waiting_table_lock: `test`.`test` 
   blocking_trx_id: 135844391
   blocking_thread: 3 --阻塞的线程
     blocking_host:
        block_port: localhost
       idle_in_trx: 90
    blcoking_query: NULL --阻塞执行的语句
1 row in set, 2 warnings (0.00 sec)

-- 发现阻塞执行的语句为空，如何找出阻塞执行的语句，只能找到上一次执行的语句，如果隔了很多条能无法识别。而且该操作执行缓慢
mysql> select * from sys.session where conn_id='3'\G;
......
        last_statement: delete from test where a = 3
......
```

