---
layout:     post                    
title:      MySQL技术栈面试问题集锦
date:       2020-10-01             
author:     Qiyibaba               
header-img: img/post-bg-202002.jpeg   
catalog: true                     
tags:                               
    - mysql
---


# MySQL技术栈面试问题集锦

**问答题：**

1. mysql有一个联合索引（abc），查询条件ab、bc、ca哪些会用到这个 索引？mysql的索引结构是什么？为什么要采用这种索引结构
2. mysql索引类型，mysql的sql优化，树的数据结构与二叉树的查询时间复杂度
3. 说下mysql使用Innodb存储引擎下索引原理，有哪些类型的索引？记录是如何组织存储的？假设以(a, b, c)的顺序创建一个复合索引，select where条件是(b=? c=?) 能命中(a, b, c)这个复合索引吗？where条件是 (a=? b > ? c=?)能命中索引中哪些字段? where条件是(a=?)查找完整记录的过程？ 说下mysql下事务特性？原子性是怎么实现的？
4. mysql主从复制原理
5. mysql中 varchar（255）和int(11)中的数字分别代表的含义
6. mysql索引，like、in、not in是否走索引？not in 通过什么方式可以代替以后做索引？
7. 以mysql vs kylin为例讲， olap与oltp的区别？能不能从底层说明为什么olap多为column-based，oltp多为row-based？
8. 在mysql innoDB中，如果一个update事务正在执行，还未commit：update sms set status = '1' where id = 100,另一个查询语句 select * from sms where id =100会不会阻塞

**程序题：**

1. 用shell或python写一段程序，满足以下要求：
   1. 清除三个mysql实例中test数据库下所有以“_YYYYMM”年月格式（近3年来）作为后缀的临时表；
   2. 遍历三个mysql实例中的数据库（系统自带的数据库不算）下所有的表，如表中没有主键、且表中含有id列，则以该列为索引键添加主键。
2. 请编写脚本实现自动创建一个mysql实例的slave实例，mysql版本为5.6\5.7，要求全程不需要人工干预，执行过程中主库正常运行不受影响，主备库数据完全一致且备库处于正常复制状态，可使用第三方开源工具，脚本语言不限。
3. 执行后台任务，创建百库relation000到relation099，每个库里创建两张表A (AA varchar(10), AB int), B (BA int, BB char(8)), 每个表插入任意数据5000万行。mysql参数，密码空：mysql -h127.1 -P3306 -uroot
4. 如何在应用层实现mysql读写分离，写写关键代码 
5. 对于一张mysql订单表，如何选出昨天销量最高的商品id 
6.  在分布式环境下，我们经常需要用到唯一单号，但是mysql的自增单号不能满足高并发的场景，请设计实现一个分布式环境下的唯一单号生成器。该生成器生成的单号需要满足：1 单号不能重复 2 分布式环境下的高并发 3 不能有单点问题。java
7. 请编写脚本实现以下功能：比对mysql版本（5.6或5.7） A实例中a库和B实例中b库中的数据行数，允许相差在100行内，超过100则输出告警日志。其中A实例server的ip地址192.168.1.10，端口3306，用户root，密码123456，库名aaa，表若干，量级平均在百万级;B实例server的ip地址192.168.1.11，端口3306，用户root，密码123456，库名bbb，表若干，量级平均在百万级。要求不能影响A实例和B实例上其他业务的读写功能，脚本语言不限。 添加
8. 在数据库运维的过程中，我们常常会遇到固定策略的巡检需求，现在希望将这一重复性工作脚本化，请根据如下提示，用您熟悉的语言，写出巡检脚本
   1. 本次巡检通过SQL查询数据库内部表得到，数据库可以通过MySQL客户端连接
   2. 为简化实现，仅做如下三项内容的检查，并提供虚拟的SQL如下所示：
      1. 查询数据库磁盘使用量select disk_usage from database.disk_stat;
      2. 查询数据库的slowquery情况：select sql_text,execute_time from database.sql_audit;
      3. 查询数据库当前的连接数select session_count from database.session_stat;
   3. 请将上述查询的结果写入文件，生成巡检报告(文件名，文件存放路径自定义) 
