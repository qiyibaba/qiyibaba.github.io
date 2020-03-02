---
layout:     post                    
title:     	分区表范围查询产生的死锁问题分析									
subtitle:   分区表范围查询产生的死锁问题分析
date:       2020-03-02            
author:     Qiyibaba               
header-img: img/post-bg-202002.jpg   
catalog: true                     
tags:                               

    - mysql
    - deadlock
---

### 死锁信息

```
## LATEST DETECTED DEADLOCK

2019-08-02 21:20:48 0x7f34ff467700
*** (1) TRANSACTION:
TRANSACTION 55455773, ACTIVE 0 sec starting index read
mysql tables in use 2, locked 2
LOCK WAIT 197 lock struct(s), heap size 24784, 140 row lock(s)
MySQL thread id 8675, OS thread handle 139865598367488, query id 58349 10.131.10.220 root Sending data
SELECT `uapuser`.`b`.`customer_id`,`uapuser`.`b`.`security_account_id`,`uapuser`.`b`.`customer_id`,`b`.`gtid` as `gtid1`,`a`.`gtid` as `gtid2` FROM (`uapuser`.`security_account` `b` JOIN `uapuser`.`edf_dormancy_acct` `a` ON ((`uapuser`.`b`.`SECURITY_ACCOUNT_ID` = `uapuser`.`a`.`ZQZH`) and (`uapuser`.`a`.`khh` = `uapuser`.`b`.`CUSTOMER_ID`)))   where ((`uapuser`.`a`.`KHH` between (100000000000 + (100 * 11)) and ((100000000000 + (100 * 11)) + 99)) or (`uapuser`.`a`.`KHH` between (100002500000 + (100 * 11)) and ((100002500000 + (100 * 11)) + 99)) or (`uapuser`.`a`.`KHH` between (100005000000 + (100 * 11)) and ((100005000000 + (100 * 11)) + 99)) or (`uapuser`.`a`.`KHH` between (100007500000 + (100 * 11)) and ((100007500000 + (100 * 11)) + 99)) or (`uapuser`.`a`.`KHH` between (100010000000 + (100 * 11)) and ((100010000000 + (100 * 11)) + 99)) or (`uapuser`.`a`.`KHH` between (100012500000 + (100 * 11)) and ((1000
*** (1) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 20631 page no 5 n bits 264 index PRIMARY of table `uapuser`.`edf_dormancy_acct` /* Partition `part_26` */ trx id 55455773 lock_mode X locks rec but not gap waiting
Record lock, heap no 2 PHYSICAL RECORD: n_fields 11; compact format; info bits 0
 0: len 8; hex 800000174c3094a5; asc     L0  ;;
 1: len 10; hex 30303632353030303034; asc 0062500004;;
 2: len 6; hex 00000244ff6a; asc    D j;;
 3: len 7; hex 5a000000201a0c; asc Z      ;;
 4: len 8; hex 800000174c3094a5; asc     L0  ;;
 5: len 12; hex 313830303632333231343033; asc 180062321403;;
 6: len 4; hex 80000000; asc     ;;
 7: len 4; hex 80000000; asc     ;;
 8: len 4; hex 7fffffff; asc     ;;
 9: len 0; hex ; asc ;;
 10: len 8; hex 0000000000000000; asc         ;;

*** (2) TRANSACTION:
TRANSACTION 55455771, ACTIVE 0 sec starting index read
mysql tables in use 2, locked 2
214 lock struct(s), heap size 24784, 272 row lock(s)
MySQL thread id 8677, OS thread handle 139865597835008, query id 58333 10.131.10.220 root Sending data
SELECT `uapuser`.`b`.`customer_id`,`uapuser`.`b`.`security_account_id`,`uapuser`.`b`.`customer_id`,`b`.`gtid` as `gtid1`,`a`.`gtid` as `gtid2` FROM (`uapuser`.`security_account` `b` JOIN `uapuser`.`edf_dormancy_acct` `a` ON ((`uapuser`.`b`.`SECURITY_ACCOUNT_ID` = `uapuser`.`a`.`ZQZH`) and (`uapuser`.`a`.`khh` = `uapuser`.`b`.`CUSTOMER_ID`)))   where ((`uapuser`.`a`.`KHH` between (100000000000 + (100 * 2)) and ((100000000000 + (100 * 2)) + 99)) or (`uapuser`.`a`.`KHH` between (100002500000 + (100 * 2)) and ((100002500000 + (100 * 2)) + 99)) or (`uapuser`.`a`.`KHH` between (100005000000 + (100 * 2)) and ((100005000000 + (100 * 2)) + 99)) or (`uapuser`.`a`.`KHH` between (100007500000 + (100 * 2)) and ((100007500000 + (100 * 2)) + 99)) or (`uapuser`.`a`.`KHH` between (100010000000 + (100 * 2)) and ((100010000000 + (100 * 2)) + 99)) or (`uapuser`.`a`.`KHH` between (100012500000 + (100 * 2)) and ((100012500000 +
*** (2) HOLDS THE LOCK(S):
RECORD LOCKS space id 20631 page no 5 n bits 264 index PRIMARY of table `uapuser`.`edf_dormancy_acct` /* Partition `part_26` */ trx id 55455771 lock_mode X locks rec but not gap
Record lock, heap no 2 PHYSICAL RECORD: n_fields 11; compact format; info bits 0
 0: len 8; hex 800000174c3094a5; asc     L0  ;;
 1: len 10; hex 30303632353030303034; asc 0062500004;;
 2: len 6; hex 00000244ff6a; asc    D j;;
 3: len 7; hex 5a000000201a0c; asc Z      ;;
 4: len 8; hex 800000174c3094a5; asc     L0  ;;
 5: len 12; hex 313830303632333231343033; asc 180062321403;;
 6: len 4; hex 80000000; asc     ;;
 7: len 4; hex 80000000; asc     ;;
 8: len 4; hex 7fffffff; asc     ;;
 9: len 0; hex ; asc ;;
 10: len 8; hex 0000000000000000; asc         ;;

*** (2) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 20639 page no 5 n bits 264 index PRIMARY of table `uapuser`.`edf_dormancy_acct` /* Partition `part_34` */ trx id 55455771 lock_mode X locks rec but not gap waiting
Record lock, heap no 2 PHYSICAL RECORD: n_fields 11; compact format; info bits 0
 0: len 8; hex 800000174d61c1a3; asc     Ma  ;;
 1: len 10; hex 30303832353030303032; asc 0082500002;;
 2: len 6; hex 00000244ff6f; asc    D o;;
 3: len 7; hex 5f0000001e0850; asc _     P;;
 4: len 8; hex 800000174d61c1a3; asc     Ma  ;;
 5: len 12; hex 313830303832323634323439; asc 180082264249;;
 6: len 4; hex 80000000; asc     ;;
 7: len 4; hex 80000000; asc     ;;
 8: len 4; hex 7fffffff; asc     ;;
 9: len 0; hex ; asc ;;
 10: len 8; hex 0000000000000000; asc         ;;
```

​	此处事务1等待26分区的第一个记录锁，主键为（100062500004，’0062500004’），事务2持有26分区的第一个记录锁，事务2等待34分区的第一个记录锁，主键为（100082500002，’0082500002’）

#### 锁等待日志信息

```
2019-08-02T21:20:48.368206 lock_wait_time:0ms req_thd_id:8675 req_trx_id:55455773 req_trx_seq:0 req_gtm_gtid:6995719040 blk_thd_id:8677 blk_trx_id:55455771 blk_trx_seq:0 blk_gtm_gtid:6995719032
2019-08-02T21:20:48.368206 lock_wait_time:0ms req_thd_id:8675 req_trx_id:55455773 req_trx_seq:0 req_gtm_gtid:6995719040 blk_thd_id:8685 blk_trx_id:55455772 blk_trx_seq:0 blk_gtm_gtid:6995719038
2019-08-02T21:20:48.368206 lock_wait_time:0ms req_thd_id:8675 req_trx_id:55455773 req_trx_seq:0 req_gtm_gtid:6995719040 blk_thd_id:8679 blk_trx_id:55455770 blk_trx_seq:0 blk_gtm_gtid:6995719034
2019-08-02T21:20:48.368452 lock_wait_time:0ms req_thd_id:8677 req_trx_id:55455771 req_trx_seq:0 req_gtm_gtid:6995719032 blk_thd_id:8675 blk_trx_id:55455773 blk_trx_seq:0 blk_gtm_gtid:6995719040
```

分析：根据锁等待日志可以发现，事务1（55455773）在等待事务2（55455771）的锁，而事务2也在同时等待事务1的锁，因此，事务1和事务2形成了死锁。

#### General_log日志信息

```
2019-08-02T21:20:47.988314+08:00         8675 Query     /*+GTID=6995719040*/start transaction;
2019-08-02T21:20:47.990261+08:00         8675 Query     SELECT `uapuser`.`b`.`customer_id`,`uapuser`.`b`.`security_account_id`,`uapuser`.`b`.`customer_id`,`b`.`gtid` as `gtid1`,`a`.`gtid` as `gtid2` FROM (`uapuser`.`security_account` `b` JOIN `uapuser`.`edf_dormancy_acct` `a` ON ((`uapuser`.`b`.`SECURITY_ACCOUNT_ID` = `uapuser`.`a`.`ZQZH`) and (`uapuser`.`a`.`khh` = `uapuser`.`b`.`CUSTOMER_ID`)))   where ((`uapuser`.`a`.`KHH` between (100000000000 + (100 * 11)) and ((100000000000 + (100 * 11)) + 99)) or ... and (`uapuser`.`a`.`MSG_CODE` = -(1))))  FOR UPDATE
2019-08-02T21:21:04.308754+08:00         8675 Query     rollback

2019-08-02T21:20:47.984183+08:00         8677 Query     /*+GTID=6995719032*/start transaction;
2019-08-02T21:20:47.986308+08:00         8677 Query     SELECT `uapuser`.`b`.`customer_id`,`uapuser`.`b`.`security_account_id`,`uapuser`.`b`.`customer_id`,`b`.`gtid` as `gtid1`,`a`.`gtid` as `gtid2` FROM (`uapuser`.`security_account` `b` JOIN `uapuser`.`edf_dormancy_acct` `a` ON ((`uapuser`.`b`.`SECURITY_ACCOUNT_ID` = `uapuser`.`a`.`ZQZH`) and (`uapuser`.`a`.`khh` = `uapuser`.`b`.`CUSTOMER_ID`)))   where ((`uapuser`.`a`.`KHH` between (100000000000 + (100 * 2)) and ((100000000000 + (100 * 2)) + 99)) or ... and (`uapuser`.`a`.`MSG_CODE` = -(1))))  FOR UPDATE
2019-08-02T21:21:04.308628+08:00         8677 Query     rollback
```

根据事务的线程号从general_log中提取2个事务的日志，2个事务本身均只做了一个select ... for update语句，而且根据where条件的规律可以判断，死锁信息中涉及的2个行记录，均不满足这2个事务的where条件。

#### 问题现象推论

- 类似select ... for update语句应该会扫描所有分区的第一行记录，并且可能需要持有锁；

- 模型简化
  由于问题场景是100多个分区表的2个表join过程，此处将问题简化为6个分区的单表查询过程。
  建表语句：Drop database if exists abczyy_part;

```mysql
Create database abczyy_part;

Use abczyy_part;

CREATE TABLE `edf_dormancy_acct` (
  `SERIAL_NO` bigint(20) NOT NULL DEFAULT '0',
  `KHH` bigint(20) NOT NULL DEFAULT '0',
  `ZQZH` varchar(20) COLLATE utf8_bin NOT NULL DEFAULT '',
  `MSG_CODE` int(11) NOT NULL DEFAULT '0',
  `GTID` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`KHH`,`ZQZH`),
  KEY `EDF_DORMANCY_ACCT_IDX1` (`KHH`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin
/*!50100 PARTITION BY RANGE (KHH)
(PARTITION part_0 VALUES LESS THAN (100000000000) ENGINE = InnoDB,
 PARTITION part_1 VALUES LESS THAN (100002500000) ENGINE = InnoDB,
 PARTITION part_2 VALUES LESS THAN (100005000000) ENGINE = InnoDB,
 PARTITION part_3 VALUES LESS THAN (100007500000) ENGINE = InnoDB,
 PARTITION part_4 VALUES LESS THAN (100010000000) ENGINE = InnoDB,
PARTITION part_5 VALUES LESS THAN (100012500000) ENGINE = InnoDB)*/;

-- 预置15条数据，1-5分区各3条数据：
insert into edf_dormancy_acct  values(100000000123,100000000123,'00000123',-1,1);
insert into edf_dormancy_acct  values(100002500123,100002500123,'02500123',10,2);
insert into edf_dormancy_acct  values(100005000123,100005000123,'05000123',-1,3);
insert into edf_dormancy_acct  values(100007500123,100007500123,'07500123',10,4);
insert into edf_dormancy_acct  values(100010000234,100010000234,'10000234',-1,5);
insert into edf_dormancy_acct  values(100000000456,100000000456,'00000456',-1,6);
insert into edf_dormancy_acct  values(100002500456,100002500456,'02500456',10,7);
insert into edf_dormancy_acct  values(100005000456,100005000456,'05000456',-1,8);
insert into edf_dormancy_acct  values(100007500456,100007500456,'07500456',10,9);
insert into edf_dormancy_acct  values(100010000567,100010000567,'10000567',-1,10);
insert into edf_dormancy_acct  values(100000000457,100000000457,'00000457',-1,11);
insert into edf_dormancy_acct  values(100002500457,100002500457,'02500457',10,12);
insert into edf_dormancy_acct  values(100005000457,100005000457,'05000457',-1,13);
insert into edf_dormancy_acct  values(100007500457,100007500457,'07500457',10,14);
insert into edf_dormancy_acct  values(100010000568,100010000568,'10000568',-1,15);
```

#### 问题复现

为了便于调试，设置innodb行锁等待时间为1年

```
set global innodb_lock_wait_timeout=31536000;
```

##### 单条语句执行后仅只有where条件中的行锁

```mysql
-- 验证：
Session 1;
Use abczyy_part;
start transaction;
SELECT khh,gtid FROM edf_dormancy_acct  where ((KHH between (100000000000 + (100 * 4)) and ((100000000000 + (100 * 4)) + 99)) or (KHH between (100010000000 + (100 * 4)) and ((100010000000 + (100 * 4)) + 99)) and (MSG_CODE = -(1)) )  FOR UPDATE;
-- 以下语句全部执行成功，没有锁等待
Session 2:
Use abczyy_part;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100000000123 for update;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100002500123 for update;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100005000123 for update;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100007500123 for update;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100010000234 for update;
```

查看innodb_trx发现，session 1的事务持有了2把行锁。

```mysql
mysql> select trx_mysql_thread_id,trx_rows_locked from information_schema.innodb_trx\G
*************************** 1. row ***************************
trx_mysql_thread_id: 15
    trx_rows_locked: 2
1 row in set (0.00 sec)
```

清理：断开所有链路，释放所有事务。

##### 分区第一行记录发生锁冲突时持有该行锁

```mysql
-- 验证：
-- Session 1:
Use abczyy_part;
start transaction;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100010000234 for update;

-- Session 2;
Use abczyy_part;
start transaction;
SELECT khh,gtid FROM edf_dormancy_acct  where ((KHH between (100000000000 + (100 * 4)) and ((100000000000 + (100 * 4)) + 99)) or (KHH between (100010000000 + (100 * 4)) and ((100010000000 + (100 * 4)) + 99)) and (MSG_CODE = -(1)) )  FOR UPDATE;
```


此时，session 2的事务挂住等待session 1的分区5第一行记录行锁。

```mysql
-- Session 1:
Commit;
--  此时，session 2的sql执行成功。
```

以下语句全部执行成功，没有锁等待

```mysql
-- Session 3:
Use abczyy_part;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100000000123 for update;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100002500123 for update;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100005000123 for update;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100007500123 for update;
```

以下语句挂住等待事务2的分区5第一行记录的行锁。

```mysql
Session 3:
Use abczyy_part;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100010000234 for update;
```

查看innodb_trx发现，session 1的事务持有了3把行锁，多出来的行锁正是曾经被阻塞的分区5的第一行记录。

```mysql
mysql> select trx_mysql_thread_id,trx_rows_locked from information_schema.innodb_trx\G
*************************** 1. row ***************************
trx_mysql_thread_id: 16
    trx_rows_locked: 3
1 row in set (0.00 sec)
```

清理：断开所有链路，释放所有事务。

##### 死锁复现

```mysql
-- 验证：
session 3:
Use abczyy_part;
start transaction;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100007500123 for update;

Session 2;
Use abczyy_part;
start transaction;
SELECT khh, gtid FROM edf_dormancy_acct where ((KHH between (100000000000 + (100 * 5)) and ((100000000000 + (100 * 5)) + 99)) or (KHH between (100002500000 + (100 * 5)) and ((100002500000 + (100 * 5)) + 99)) or (KHH between (100005000000 + (100 * 5)) and ((100005000000 + (100 * 5)) + 99)) or (KHH between (100007500000 + (100 * 5)) and ((100007500000 + (100 * 5)) + 99)) or (KHH between (100010000000 + (100 * 5)) and ((100010000000 + (100 * 5)) + 99)) and (MSG_CODE = -(1)))  FOR UPDATE;
-- 此时，session 2的sql挂住等待

session 4:
Use abczyy_part;
start transaction;
SELECT khh,gtid FROM edf_dormancy_acct where KHH = 100005000123 for update;

Session 1;
Use abczyy_part;
start transaction;
SELECT khh, gtid FROM edf_dormancy_acct where ((KHH between (100000000000 + (100 * 4)) and ((100000000000 + (100 * 4)) + 99)) or (KHH between (100002500000 + (100 * 4)) and ((100002500000 + (100 * 4)) + 99)) or (KHH between (100005000000 + (100 * 4)) and ((100005000000 + (100 * 4)) + 99)) or (KHH between (100007500000 + (100 * 4)) and ((100007500000 + (100 * 4)) + 99)) or (KHH between (100010000000 + (100 * 4)) and ((100010000000 + (100 * 4)) + 99)) and (MSG_CODE = -(1)))  FOR UPDATE;
-- 此时，session 1的sql挂住等待

session 4:
commit;

session 3:
commit;

-- 结果：
-- 1）session 1事务触发死锁报错：
ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction
```

##### 死锁信息

```
## LATEST DETECTED DEADLOCK

2019-08-06 17:01:39 0x7f855ed76700
*** (1) TRANSACTION:
TRANSACTION 51732580, ACTIVE 13 sec starting index read
mysql tables in use 1, locked 1
LOCK WAIT 8 lock struct(s), heap size 1160, 4 row lock(s)
MySQL thread id 15, OS thread handle 140210797487872, query id 492 localhost root Sending data
SELECT khh, gtid FROM edf_dormancy_acct where ((KHH between (100000000000 + (100 * 4)) and ((100000000000 + (100 * 4)) + 99)) or (KHH between (100002500000 + (100 * 4)) and ((100002500000 + (100 * 4)) + 99)) or (KHH between (100005000000 + (100 * 4)) and ((100005000000 + (100 * 4)) + 99)) or (KHH between (100007500000 + (100 * 4)) and ((100007500000 + (100 * 4)) + 99)) or (KHH between (100010000000 + (100 * 4)) and ((100010000000 + (100 * 4)) + 99)) and (MSG_CODE = -(1)))  FOR UPDATE
*** (1) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 1796 page no 3 n bits 72 index PRIMARY of table `abczyy_part`.`edf_dormancy_acct` /* Partition `part_4` */ trx id 51732580 lock_mode X locks rec but not gap waiting
Record lock, heap no 2 PHYSICAL RECORD: n_fields 7; compact format; info bits 0
 0: len 8; hex 8000001748e9595b; asc     H Y[;;
 1: len 8; hex 3037353030313233; asc 07500123;;
 2: len 6; hex 000003156030; asc     `0;;
 3: len 7; hex f4000000230110; asc     #  ;;
 4: len 8; hex 8000001748e9595b; asc     H Y[;;
 5: len 4; hex 8000000a; asc     ;;
 6: len 8; hex 8000000000000004; asc         ;;

*** (2) TRANSACTION:
TRANSACTION 51732578, ACTIVE 31 sec starting index read
mysql tables in use 1, locked 1
10 lock struct(s), heap size 1160, 2 row lock(s)
MySQL thread id 16, OS thread handle 140210798552832, query id 484 localhost root Sending data
SELECT khh, gtid FROM edf_dormancy_acct where ((KHH between (100000000000 + (100 * 5)) and ((100000000000 + (100 * 5)) + 99)) or (KHH between (100002500000 + (100 * 5)) and ((100002500000 + (100 * 5)) + 99)) or (KHH between (100005000000 + (100 * 5)) and ((100005000000 + (100 * 5)) + 99)) or (KHH between (100007500000 + (100 * 5)) and ((100007500000 + (100 * 5)) + 99)) or (KHH between (100010000000 + (100 * 5)) and ((100010000000 + (100 * 5)) + 99)) and (MSG_CODE = -(1)))  FOR UPDATE
*** (2) HOLDS THE LOCK(S):
RECORD LOCKS space id 1796 page no 3 n bits 72 index PRIMARY of table `abczyy_part`.`edf_dormancy_acct` /* Partition `part_4` */ trx id 51732578 lock_mode X locks rec but not gap
Record lock, heap no 2 PHYSICAL RECORD: n_fields 7; compact format; info bits 0
 0: len 8; hex 8000001748e9595b; asc     H Y[;;
 1: len 8; hex 3037353030313233; asc 07500123;;
 2: len 6; hex 000003156030; asc     `0;;
 3: len 7; hex f4000000230110; asc     #  ;;
 4: len 8; hex 8000001748e9595b; asc     H Y[;;
 5: len 4; hex 8000000a; asc     ;;
 6: len 8; hex 8000000000000004; asc         ;;

*** (2) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 1795 page no 3 n bits 72 index PRIMARY of table `abczyy_part`.`edf_dormancy_acct` /* Partition `part_3` */ trx id 51732578 lock_mode X locks rec but not gap waiting
Record lock, heap no 2 PHYSICAL RECORD: n_fields 7; compact format; info bits 0
 0: len 8; hex 8000001748c333bb; asc     H 3 ;;
 1: len 8; hex 3035303030313233; asc 05000123;;
 2: len 6; hex 00000315602f; asc     `/;;
 3: len 7; hex f30000010b0110; asc        ;;
 4: len 8; hex 8000001748c333bb; asc     H 3 ;;
 5: len 4; hex 7fffffff; asc     ;;
 6: len 8; hex 8000000000000003; asc         ;;

*** WE ROLL BACK TRANSACTION (1)
```

##### 总结

1. select ... for update分区表范围查询时需要扫描条件内涉及到所有的分区的第一行记录。假设涉及到N个分区，那么扫描次数为N*N次。
2. 对于分区的第一行记录，如果不在查询条件范围内，则会在扫描到该记录时先持有锁，再释放锁，重复N次
3. 对于分区的第一行记录，如果不在查询条件范围内，且在扫描时发生了行锁冲突，则将会持有该记录行X锁，直至事务结束。

##### 问题原因

- 本质原因“分区表在范围查询场景下，扫描分区第一行记录时，在锁冲突场景下会持有该锁直至事务结束。

- 代码级简单分析

  ```
  - 主要涉及三个函数：
    ha_innopart::read_range_first_in_part
    row_unlock_for_mysql()
    row_search_mvcc()
  
  主要逻辑如下：
  ha_innopart::read_range_first_in_part
  1）调用ha_innobase::index_read()函数读取分区的第一条记录；
  2）如果没有满足>begin条件的记录，返回HA_ERR_END_OF_FILE；
  3）如果满足>begin条件记录，但是在判断不满足<end条件，返回HA_ERR_END_OF_FILE，次过程需要调用ha_innobase::unlock_row()释放该记录的行锁；
  4）如果满足>begin ，<end条件，返回成功。
  正常情况，读取每一个分区第一个记录后，很快就在第三步中释放该行锁了。在释放行锁的函数row_unlock_for_mysql()中，只有当prebuilt->new_rec_locks >= 1才会释放相应的行锁。
  但是对于特殊的场景：调用index_read()读取分区第一条记录时，如果产生锁等待，当锁等待结束后，获取到该行锁后，在row_search_mvcc()函数中将会重置prebuilt->new_rec_locks=0，在第三步的判断中，prebuilt->new_rec_locks=0，因此不会释放不属于自身where条件的行锁。
  ```

- 问题解决：从业务角度，针对该问题，可以将多个or条件的一个语句拆分为多个语句，这些语句在一个事务内，这样将不会出现死锁问题。

