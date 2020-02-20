---
layout:     post                    
title:      MySQL建表及存储内幕篇一  
subtitle:   MySQL建表的限制，如何选择行存储模式。
date:       2020-02-20            
author:     Qiyibaba               
header-img: img/post-bg-202002.jpeg   
catalog: true                     
tags:                               
    - mysql
    - row_format
---



**MySQL建表要求表每行最大字节数是65535字节,索引长度为3072字节**

#### 行长度限制

```mysql
mysql> create table  t1(id int,col varchar(65535)) DEFAULT CHARSET=utf8mb4;
ERROR 1074 (42000): Column length too big for column 'col' (max = 16383); use BLOB or TEXT instead
```

​	这个错误是varchar字段长度不能超过16383为什么限制max是16383，因为编码字符集为utf8mb4，则一个字符占4个字节，那么总的字符长度为65535/4=16383

```mysql
mysql> create table  t1(id int,col varchar(16383)) DEFAULT CHARSET=utf8mb4;
ERROR 1118 (42000): Row size too large. The maximum row size for the used table type, not counting BLOBs, is 65535. This includes storage overhead, check the manual. You have to change some columns to TEXT or BLOBs
```

​	这边就是表的限制，表要求行长度不超过65535，16383*4+4=65536,超过长度限制，需要再去掉一位，其中4就是int型占用的字节数。

#### 索引长度限制

​	MySQL索引长度在5.6里面默认不能超过767bytes，5.7不超过3072bytes

```mysql
mysql> create table  t2(id varchar(769) primary key,col varchar(15000)) DEFAULT CHARSET=utf8mb4;
ERROR 1071 (42000): Specified key was too long; max key length is 3072 bytes
```

​	将索引字段降低长度，则创建成功：

```mysql
mysql> create table  t2(id varchar(768) primary key,col varchar(15000)) DEFAULT CHARSET=utf8mb4;
Query OK, 0 rows affected (0.00 sec)
```

​	按照上面的描述，一行记录最大可以支持到65535byte，即64k，而MySQL默认页面大小的配置是16K，那如此大的数据又是如何存储的呢，就需要进行下面的讲解，MySQL的存储模式



```mysql
mysql> show variables like 'innodb_%_format';
+---------------------------+-----------+
| Variable_name             | Value     |
+---------------------------+-----------+
| innodb_default_row_format | dynamic   |
| innodb_file_format        | Barracuda |
+---------------------------+-----------+
2 rows in set (0.00 sec)
```



行记录格式表

| row format | 紧凑存储 | 增强可变长度列存储 | 长索引 | 压缩 | 表空间                          | 文件格式            |
| ---------- | -------- | ------------------ | ------ | ---- | ------------------------------- | ------------------- |
| redundant  | no       | no                 | no     | no   | system, file-per-table, general | Antelope, Barracuda |
| compact    | yes      | no                 | no     | no   | system, file-per-table, general | Antelope, Barracuda |
| dynamic(D) | yes      | yes                | yes    | no   | system, file-per-table, general | Barracuda           |
| compressed | yes      | yes                | yes    | yes  | file-per-table, general         | Barracuda           |

#### REDUNDANT

​	B-tree存储变长列（VARCHAR, VARBINARY, BLOB, TEXT）的前768字节，剩下的部分存储在溢出页中。固定长度列，超过768字节的视为变长列。

- 索引的每条记录包含一个6字节长度的头部。头部用来将连续的记录连接在一起，并用于行锁。
- 聚簇索引的记录包含用户定于的所有列。另外还有一个6字节的事务ID（DB_TRX_ID）和一个7字节长度的回滚段指针(Roll pointer)列。
- 如果没定于主键，每个聚簇索引行还包括一个6字节的行ID（row ID）字段。
- 每个二级索引记录包含所有定义的主键索引列。
- 一条记录包含一个指针来指向这条记录的每个列。如果一条记录的列的总长度小于128字节，这个指针是一个字节，否则为2个字节。这个指针数组称为记录目录（record directory）。指针指向的区域是这条记录的数据部分。
- 在内部，固定长度的字符字段比如 CHAR(10)通过固定长度的格式存储。尾部填充空格。
- 固定长度字段长度大于或者等于768字节将被编码成变长的字段，存储在页外区域。
- 一个SQL的NULL值存储一个字节或者两个字节在记录目录（record dirictoty）。对于变长字段null 值在数据区域存储0个字节。对于固定长度的字段，依然存储固定长度在数据部分。为null值保留固定长度空间允许列从null值更新为非空值而不会引起索引的分裂。

#### COMPACT

​	compact格式比redundant存储空间减少20%。如果受限于cache命中和磁盘速度，compact格式会快一些，若受限于CPU速度，compact格式会慢一些。compact格式存储变长列的前768字节于B-tree节点中，剩余部分存储在溢出页中。固定长度大于768字节的以变长列方式处理。

- 索引的每条记录包含一个5个字节的头部，头部前面可以有一个可变长度的头部。这个头部用来将相关连的记录链接在一起，也用于行锁。
- 记录头部的变长部分包含了一个表示null 值的位向量(bit vector)。如果索引中可以为null的字段数量为N，这个位向量包含 N/8 向上取整的字节数。比例如果有9-16个字段可以为NULL值，这个位向量使用两个字节。为NULL的列不占用空间，只占用这个位向量中的位。头部的变长部分还包含了变长字段的长度。每个长度占用一个或者2个字节，这取决了字段的最大长度。如果所有列都可以为null 并且制定了固定长度，记录头部就没有变长部分。
- 对每个不为NULL的变长字段，记录头包含了一个字节或者两个字节的字段长度。只有当字段存储在外部的溢出区域或者字段最大长度超过255字节并且实际长度超过127个字节的时候会使用2个字节的记录头部。对应外部存储的字段，两个字节的长度指明内部存储部分的长度加上指向外部存储部分的20个字节的指针。内部部分是768字节，因此这个长度值为 768+20. 20个字节的指针存储了这个字段的真实长度。
- 记录头部跟着非空字段的数据部分。
- 聚簇索引的记录包含了所以用户定于的字段。另外还有一个6字节的事务ID列和一个7字节的回滚段指针。
- 如果没有定于主键索引，则聚簇索引还包括一个6字节的Row ID列。
- 每个辅助索引记录包含为群集索引键定义的不在辅助索引中的所有主键列。如果任何一个主键列是可变长度的，那么每个辅助索引的记录头都有一个可变长度的部分来记录它们的长度，即使辅助索引是在固定长度的列上定义的
- 在内部，对于固定长度的字符集，固定长度的字段存储在固定长度的格式存储，比如 CHAR(10)
- 对于变长的字符集，比如 uft8mb3和utf8mb4, InnoDB试图用N字节来存储 CHAR(N)。如果CHAR(N)列的值的长度超过N字节，列后面的空格减少到最小值。CHAR(N)列值的最大长度是最大字符编码数 x N。比如utf8mb4字符集的最长编码为4，则列的最长字节数是 4*N.

#### DYNAMIC

​	基于compact格式，提高存储容量，支持大索引（large index）3072字节，由innodb_large_prefix参数控制。创建大索引实例见第一部分。

```mysql
mysql> show variables like 'innodb_large_prefix';
+---------------------+-------+
| Variable_name       | Value |
+---------------------+-------+
| innodb_large_prefix | ON    |
+---------------------+-------+
1 row in set (0.00 sec)
```

1. 行格式为dynamic时，变长列为完全页外存储，聚集索引记录包含一个20字节的指针指向溢出页。固定长度列，超过768字节时，以变长列方式存储。列是否存储在页外时，
2. 依赖页大小和行总大小。当一行太长时，将选择最长的列作为页外存储，直到聚集索引记录适合于B-tree页面。TEXT或者BLOB列小于40字节的存储一行。
3. 可采用系统表空间，独立表空间，普通表空间。

#### COMPRESSED

​	基于dynamic格式，支持表和索引数据压缩。compressed行格式采用dynamic相同的页外存储细节，和额外的需要压缩的表和索引数据存储，更小的页大小。KEY_BLOCK_SIZE参数控制由多少列数据存储在聚集索引，多少存储在溢出页。Innodb_file_per_table变量必须开启，innodb_file_format必须是barracuda

#### dynamic验证
- 创建2张表，都包含一个长varchar字段，其中一个行总长度为8k，另一个总长度为60k，验证下2中不同表的存储方式

  ```mysql
  create table t1(id int primary key,col varchar(2047)) DEFAULT CHARSET=utf8mb4;
  create table t2(id int primary key,col varchar(15359)) DEFAULT CHARSET=utf8mb4;
  ```

- 各往每张表中插入一条数据

  ```mysql
  mysql> insert into t1 values (1,repeat('a',2047));
  Query OK, 1 row affected (0.01 sec)
  
  mysql> insert into t2 values (1,repeat('a',15359));
  Query OK, 1 row affected (0.01 sec)
  ```

- 查看页面信息

  ```mysql
  mysql> select table_name,page_number, page_type, number_records, data_size from information_schema.INNODB_BUFFER_PAGE where table_name like '%test%';
  +-------------+-------------+-----------+----------------+-----------+
  | table_name  | page_number | page_type | number_records | data_size |
  +-------------+-------------+-----------+----------------+-----------+
  | `test`.`t1` |           3 | INDEX     |              5 |     10390 |
  | `test`.`t2` |           3 | INDEX     |              5 |      8363 |
  +-------------+-------------+-----------+----------------+-----------+
  2 rows in set (0.11 sec)
  ```

- 都只有一个页存了5条数据，看到data_size并未相差很多，也就是实际加载到缓存中的数据并未相差很大，实际数据应该差别很大，我们需要看下实际数据存储的信息：

  ```
  [ltdb1@db02 analysisIBD]$ python py_innodb_page_info.py ../data/data/test/t2.ibd -v
  page offset 00000000, page type <File Space Header>
  page offset 00000001, page type <Insert Buffer Bitmap>
  page offset 00000002, page type <File Segment inode>
  page offset 00000003, page type <B-tree Node>, page level <0000>
  page offset 00000004, page type <Uncompressed BLOB Page>
  page offset 00000000, page type <Freshly Allocated Page>
  Total number of page: 6:
  Insert Buffer Bitmap: 1
  Freshly Allocated Page: 1
  File Segment inode: 1
  B-tree Node: 1
  File Space Header: 1
  Uncompressed BLOB Page: 1
  [ltdb1@db02 analysisIBD]$ python py_innodb_page_info.py ../data/data/test/t1.ibd -v
  page offset 00000000, page type <File Space Header>
  page offset 00000001, page type <Insert Buffer Bitmap>
  page offset 00000002, page type <File Segment inode>
  page offset 00000003, page type <B-tree Node>, page level <0000>
  page offset 00000000, page type <Freshly Allocated Page>
  page offset 00000000, page type <Freshly Allocated Page>
  Total number of page: 6:
  Freshly Allocated Page: 2
  Insert Buffer Bitmap: 1
  File Space Header: 1
  B-tree Node: 1
  File Segment inode: 1
  ```

- ibd文件解析，未完待续....

### 参考文档：

https://dev.mysql.com/doc/refman/5.7/en/column-count-limit.html
https://dev.mysql.com/doc/refman/5.7/en/innodb-row-format.html
