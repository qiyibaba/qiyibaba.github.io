---
layout:     post                    
title:     	innodb索引体系架构									
subtitle:   MySQL技术内幕 InnoDB存储引擎索引体系架构及扩展
date:       2020-03-03            
author:     Qiyibaba               
header-img: img/post-bg-202003.jpg   
catalog: true                     
tags:                               
    - mysql
    - innodb
---

### 索引组织

在InnoDB存储存储引擎中，表都是根据主键顺序组织存放的，这种存储方式的表称为索引组织表。在InnoDB存储引擎中，每张表都有个主键（primary key）。如果在创建表时没有显示地定义主键，则InnoDB存储引擎会按如下方式选择或创建主键：

1. 首先判断表中是否有非空的唯一索引（Unique NOT NULL），如果有，则该列为主键；
2. 如果不存在非空唯一索引，InnoDB存储引擎自动创建一个6字节大小的指针。

当表中有多个非空唯一索引时，InnoDB存储引擎将选择建表时第一个定义的非空唯一索引为主键。这里需要注意的是，主键的选择根据的是定义索引的顺序，而不是建表时列的顺序。

```mysql
-- 表a有单字段的主键，可以通过_rowid查看主键信息，主键信息就是主键字段
mysql> create table a(id int not null,b int primary key);
Query OK, 0 rows affected (2.58 sec)

mysql> insert into a values (2,20),(3,30);
Query OK, 2 rows affected (1.34 sec)
Records: 2  Duplicates: 0  Warnings: 0

mysql> select *,_rowid from a;
+----+----+--------+
| id | b  | _rowid |
+----+----+--------+
|  2 | 20 |     20 |
|  3 | 30 |     30 |
+----+----+--------+
2 rows in set (0.00 sec)

-- 表b没有主键字段，有2个非空的唯一的索引，mysql默认选择第一个创建的非空的非唯一索引作为主键，通过_rowid查看主键信息，主键信息就是d字段
mysql> create table b(a int not null,b int,c int not null,d int not null,unique key(b),unique key (d),unique key(c));
Query OK, 0 rows affected (0.07 sec)

Query OK, 2 rows affected (0.00 sec)
Records: 2  Duplicates: 0  Warnings: 0

mysql> select *,_rowid from b;
+---+------+---+---+--------+
| a | b    | c | d | _rowid |
+---+------+---+---+--------+
| 1 |    2 | 3 | 4 |      4 |
| 5 |    6 | 7 | 8 |      8 |
+---+------+---+---+--------+
2 rows in set (0.08 sec)

-- 表c没有主键字段，并且没有非空的唯一索引列，则无法通过_rowid查看主键信息，主键为默认的指针
mysql> create table c(a int not null,b int,c int not null,d int not null,unique key(b),key (d),key(c));
Query OK, 0 rows affected (0.43 sec)

mysql> insert into c values (1,2,3,4),(5,6,7,8);
Query OK, 2 rows affected (0.63 sec)
Records: 2  Duplicates: 0  Warnings: 0

mysql> select *,_rowid from c;
ERROR 1054 (42S22): Unknown column '_rowid' in 'field list'

-- 表e使用的联合主键，则无法通过_rowid查看主键信息
mysql> insert into e values(1,2),(3,4);
Query OK, 2 rows affected (0.54 sec)
Records: 2  Duplicates: 0  Warnings: 0

mysql> select *,_rowid from e;
ERROR 1054 (42S22): Unknown column '_rowid' in 'field list'
```

### innodb逻辑存储结构

InnoDB存储引擎的逻辑存储结构和Oracle大致相同，所有数据都被逻辑地存放在一个空间中，我们称之为表空间（tablespace）。表空间又由段（segment）、区（extent）、页（page）组成。页在一些文档中有时也称为块（block），1 extent = 64 pages。

#### 表空间

表空间可以看做是InnoDB存储引擎逻辑结构的最高层，所有的数据都是存放在表空间中。默认情况下InnoDB存储引擎有一个共享表空间ibdata1，即所有数据都放在这个表空间内。

> 推荐：启用参数innodb_file_per_table，则每张表内的数据可以单独放到一个表空间内。

对于启用了innodb_file_per_table的参数选项，需要注意的是，每张表的表空间内存放的只是数据、索引和插入缓冲，其他类的数据，如撤销（Undo）信息、系统事务信息、二次写缓冲（double write buffer）等还是存放在原来的共享表空间内。这也就说明了另一个问题：即使在启用了参数innodb_file_per_table之后，共享表空间还是会不断地增加其大小。

#### 段

表空间是由各个段组成的，常见的段有数据段、索引段、回滚段等。InnoDB存储引擎表是索引组织的（index organized），因此数据即索引，索引即数据。那么数据段即为B+树的页节点（上图的leaf node segment），索引段即为B+树的非索引节点（上图的non-leaf node segment）。

需要注意的是，并不是每个对象都有段。因此更准确地说，表空间是由分散的页和段组成。

#### 区

区是由64个连续的页组成的，每个页大小为16KB，即每个区的大小为1MB。对于大的数据段，InnoDB存储引擎最多每次可以申请4个区，以此来保证数据的顺序性能。

在我们启用了参数innodb_file_per_table后，创建的表默认大小是96KB。区是64个连续的页，那创建的表的大小至少是1MB才对啊？其实这是因为在每个段开始时，先有32个页大小的碎片页（fragment page）来存放数据，当这些页使用完之后才是64个连续页的申请，超过64M后连续申请4个区。

```mysql
mysql> create table t1(col1 int not null auto_increment primary key,col2 varchar(7000));
Query OK, 0 rows affected (0.03 sec)

mysql> insert into t1 select null,repeat('a',7000);
Query OK, 1 row affected (0.01 sec)
Records: 1  Duplicates: 0  Warnings: 0
-- 创建了t1表，col2字段设为varchar(7000),这样能保证一个页中可以存放2条记录。可以看到，初始创建完t1后表空间默认大小为96KB
```

查看页面信息：

```
page offset 00000000, page type <File Space Header>
page offset 00000001, page type <Insert Buffer Bitmap>
page offset 00000002, page type <File Segment inode>
page offset 00000003, page type <B-tree Node>, page level <0000>
page offset 00000000, page type <Freshly Allocated Page>
page offset 00000000, page type <Freshly Allocated Page>
Total number of page:6
Insert Buffer Bitmap:1
File Segment inode:1
B-tree Node:5
File Space Header:1
Freshly Allocated Page:2
```

#### 页

同大多数数据库一样，InnoDB有页（page）的概念（也可以称为块），页是InnoDB磁盘管理的最小单位。与Oracle类似的是，Microsoft SQL Server数据库默认每页大小为8KB，不同于InnoDB页的大小（16KB），且不可以更改（也许通过更改源码可以）。[常见的页类型](https://qiyibaba.github.io/2020/02/21/ibdparse/)

#### 行

InnoDB存储引擎是面向行的（row-oriented），也就是说数据的存放按行进行存放。每个页存放的行记录也是有硬性定义的，最多允许存放16KB/2～200行的记录，即7992行记录。这里提到面向行（row-oriented）的数据库，那么也就是说，还存在有面向列（column-orientied）的数据库。MySQL infobright储存引擎就是按列来存放数据的，这对于数据仓库下的分析类SQL语句的执行以及数据压缩很有好处。类似的数据库还有Sybase IQ、Google Big Table。面向列的数据库是当前数据库发展的一个方向。

### innodb行记录格式

通过命令SHOW TABLE STATUS LIKE 'table_name'来查看当前表使用的行格式，其中row_format就代表了当前使用的行记录结构类型。例如：

```
mysql> show table status like 't1'\G;
*************************** 1. row ***************************
           Name: t1
         Engine: InnoDB
        Version: 10
     Row_format: Dynamic -1.0版本后的格式
           Rows: 6
 Avg_row_length: 13653
    Data_length: 81920
Max_data_length: 0
   Index_length: 0
      Data_free: 0
 Auto_increment: 7
    Create_time: 2018-03-27 18:34:23
    Update_time: 2018-03-27 18:38:02
     Check_time: NULL
      Collation: utf8_general_ci
       Checksum: NULL
 Create_options:
        Comment:
1 row in set (0.00 sec)
```

MySQL 5.1 中的 innodb_plugin 引入了新的文件格式：Barracuda（将以前的行格式 compact 和 redundant 合称为Antelope），该文件格式拥有新的两种行格式：compressed和dynamic。

详细格式说明见博文：[MySQL建表及存储内幕上篇](https://qiyibaba.github.io/2020/02/20/mysql-rowformat/)

### Innodb数据页结构分析

#### 数据查询分析

初始化一张表并插入数据

```mysql
mysql> desc sbtest1;
+-------+------------------+------+-----+---------+-------+
| Field | Type             | Null | Key | Default | Extra |
+-------+------------------+------+-----+---------+-------+
| id    | int(10) unsigned | NO   | PRI | NULL    |       |
| k     | int(10) unsigned | NO   | MUL | 0       |       |
| c     | char(120)        | NO   |     |         |       |
| pad   | char(60)         | NO   |     |         |       |
| GTID  | int(10) unsigned | NO   |     | 0       |       |
+-------+------------------+------+-----+---------+-------+
5 rows in set (0.04 sec)
```

解析数据文件获取页信息：

```
page offset 00000085, page type <B-tree Node>, page level <0000>
File Header Info:
FIL_PAGE_SPACE_OR_CHECKSUM           b43d2ab7
FIL_PAGE_OFFSET                      00000085
FIL_PAGE_PREV                        00000084
FIL_PAGE_NEXT                        00000086
FIL_PAGE_LSN                         0000007ed12f5c8f
FIL_PAGE_TYPE                        45bf
FIL_PAGE_FILE_FLUSH_LSN              0000000000000000
FIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID     00000040
Data Info:
Total record size is 71 ,record position from 007f to 3b44
Page Header Info :
PAGE_N_DIR_SLOTS     0012
PAGE_HEAD_TOP        3b44
PAGE_N_HEAP          8049
PAGE_FREE            0000
PAGE_GARBAGE         0000
PAGE_LAST_INSER      3a77
PAGE_DIRECTION       0002
PAGE_N_DIRECTION     0046
PAGE_N_RECS          0047
PAGE_MAX_TRX_ID      0000000000000000
PAGE_LEVEL           0000
PAGE_INDEX_ID        0000000000000041
PAGE_BTR_SEG_LEAF    00000000000000000000
PAGE_BTR_SEG_TOP     00000000000000000000
Infimum Record Info:
record header : 010002001c
Value         : 696e66696d756d00
Supremum Record Info:
record header : 08000b0000
Value         : 73757072656d756d
Page Dictionary Info:
0063 02fb 064b 099b 0ceb 103b 138b 16db 1a2b 1d7b 20cb 241b 276b 2abb 2e0b 315b 34ab 0070 
```

 现在要在页面中需找主键数据7190，步骤如下：

- 2分法获取槽位，low=0，up=18-1=17，(1+18)/2 = 9,从9号槽位开始搜索，槽位偏移量为1d7b，数据为：

```
01d70: 00  00  00  00  3c  78  04  01  28  00  d4  00  00  1c  03  00(7171) 
01d80: 00  4b  e9  0c  09  a9  00  00  01  38  2e  88  05  c2  1f  41 
01d90: 31  30  36  30  31  31  39  33  33  31  34  2d  36  31  33  37  
01da0: 31  30  32  34  35  33  32  2d  31  38  37  36  35  39  35  37  
01db0: 32  31  33  2d  30  32  38  33  34  31  38  31  31  37  38  2d  
01dc0: 33  31  30  33  39  30  39  30  38  34  39  2d  30  32  37  33  
01dd0: 35  35  37  34  30  35  30  2d  31  30  36  32  37  33  31  39  
01de0: 35  30  37  2d  33  32  35  33  34  35  34  35  38  38  32  2d  
01df0: 34  34  36  35  34  32  37  34  33  30  35  2d  35  35  32  37  
01e00: 32  35  34  38  35  37  37  20  33  30  39  39  38  36  34  33  
01e10: 32  38  39  2d  38  31  32  37  33  32  37  35  35  33  32  2d  
01e20: 38  30  32  38  33  32  30  36  38  39  35  2d  35  36  36  34  
01e30: 38  35  34  34  36  36  37  2d  39  39  39  36  39  32  38  39  
01e40: 35  35  36  20  00  00  00  00  3c  78  00  01  30  00  d4  00  
01e50: 00  1c  04
......
```

- 判断出主键值为7171，小于7190，则继续二分，low的值为9，查找槽位号为（9+17）/2=13，槽位偏移量为2abb，主键为“00  00  1c  13”，值为7187，小于7190，继续二分


- 槽位号为（13+17）/2=15，偏移量为315b，主键值为“00  00  1c  1b”，值为7195，大于7190，向下二分


- Low=13，up=15，二分值为（13+15）/2=14，偏移量为2e0b，值“00  00  1c  17”，7191，大于7190，继续向下二分


- 此时up=14，low=13，此时up-low>1为false，退出二分查找，值落在槽位号为13中。


- 遍历13号槽位，如果有值在该槽位中，否则就没有该值 


- 遍历该槽位中的数据，数据偏移量为2abb，值的前2个字节表示下一条记录距离本条记录的偏移量“**00  d4**”，遍历数据：


| 当前记录位置 | 当前数据       | 值         | 下条数据偏移量 | 下条数据地址 |
| ------------ | -------------- | ---------- | -------------- | ------------ |
| 2abb         | 00  00  1c  13 | 7187       | **00  d4**     | 2B8F         |
| 2B8F         | 00  00  1c  14 | 7188       | **00  d4**     | 2C63         |
| 2C63         | 00  00  1c  15 | 7189       | **00  d4**     | 2D37         |
| 2D37         | 00  00  1c  16 | 7190（OK） | **00  d4**     | ----         |

####  其他说明

- 删除数据槽位的首条数据后:


```
Page Dictionary Info:(数据删除前)
0063 02fb 064b 099b 0ceb 103b 138b 16db 1a2b 1d7b 20cb 241b 276b 2abb 2e0b 315b 34ab 0070 
Page Dictionary Info:(数据删除后)
0063 02fb 064b 099b 0ceb 103b 138b 16db 1a2b 20cb 241b 276b 2abb 2e0b 315b 34ab 0070 
```

结论:在删除数据后,在1d7b位置上仍然后数据存在,但是槽位指向的第一条数据,已经修改为20cb,即删除了一个槽位.7171该条数据的数据信息更新,下条数据的偏移量清0:

```
更新后:01d70: 00  00  00  00  3c  78  24  01  28  00  00  00  00  1c  03  00  
更新前:01d70: 00  00  00  00  3c  78  04  01  28  00  d4  00  00  1c  03  00 
```

7171该条数据的上一条数据,偏移量从212个字节变成了424个字节:

```
更新后:01ca0: 3c  78  00  01  20  01  a8  00  00  1c  02  00  00  4b  e9  0c   
更新前:01ca0: 3c  78  00  01  20  00  d4  00  00  1c  02  00  00  4b  e9  0c  
```

同理,删除槽位中的数据,对应的下条数据的偏移量会修改,跳过已经删除的数据.

同时:会更新page header info,将删除数据的地址添加到PAGE_FREE的地址上:

```
Page Header Info :
PAGE_N_DIR_SLOTS     0011
PAGE_HEAD_TOP        3b44
PAGE_N_HEAP          8049
PAGE_FREE            1d7b
```

-  槽位中的数据存储:


innodb为了快速查找记录，在body的后面定义了一个称之为directory的目录槽（slots）,每个槽位占用两个字节，采用的是逆序存储，也就是说mifimum的槽位总是在body最后2个字节上，其他的一次类推。每个槽位可以存储多个纪录。以下是各种slot的记录数描述范围（n_owned）：

1. Infimum slot owned 只有一条记录
2. supremum slot owned 1到8条记录
3. 普通slot owned 4到8条记录

如果普通slot在插入新的一条记录时，普通slot或者supremum管理的记录数是8，这个时候会对supremum进行split，产生一个slots，所以它的范围是从4开始。

#### 数据页根节点分析

解析出整个idb文件发现,有2个level等于1的页,如下图所示:

```
page offset 00000003, page type <B-tree Node>, page level <0001>
page offset 00000004, page type <B-tree Node>, page level <0001>
```

为什么会有2个,一个是聚集索引的根节点,一个是索引树的根节点,下面就将分别对2个根节点的树做解析

##### 聚集索引根节点

编号为003的页解析出基本信息如下:

```
page offset 00000003, page type <B-tree Node>, page level <0001>
File Header Info:
FIL_PAGE_SPACE_OR_CHECKSUM           4b1e8227
FIL_PAGE_OFFSET                      00000003
FIL_PAGE_PREV                        ffffffff
FIL_PAGE_NEXT                        ffffffff
FIL_PAGE_LSN                         0000007ed13b233f
FIL_PAGE_TYPE                        45bf
FIL_PAGE_FILE_FLUSH_LSN              0000000000000000
FIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID     00000040
Data Info:
Total record size is 142 ,record position from 007f to 07ae
Page Header Info :
PAGE_N_DIR_SLOTS     0024
PAGE_HEAD_TOP        07ae
PAGE_N_HEAP          8090
PAGE_FREE            0000
PAGE_GARBAGE         0000
PAGE_LAST_INSER      07a6
PAGE_DIRECTION       0002
PAGE_N_DIRECTION     008d
PAGE_N_RECS          008e
PAGE_MAX_TRX_ID      0000000000000000
PAGE_LEVEL           0001
PAGE_INDEX_ID        0000000000000041
PAGE_BTR_SEG_LEAF    000000400000000200f2
PAGE_BTR_SEG_TOP     00000040000000020032
Infimum Record Info:
record header : 010002001a
Value         : 696e66696d756d00
Supremum Record Info:
record header : 07000b0000
Value         : 73757072656d756d
Page Dictionary Info:
0063 00a4 00d8 010c 0140 0174 01a8 01dc 0210 0244 0278 02ac 02e0 0314 0348 037c 03b0 03e4 0418 044c 0480 04b4 04e8 051c 0550 0584 05b8 05ec 0620 0654 0688 06bc 06f0 0724 0758 0070 
```

数据分析，分析第一个槽位从00a4-00d8：

```
000a0: 00  29  00  0d  00  00  00  b2  00  00  00  08  00  00  31  00  
000b0: 0d  00  00  00  f9  00  00  00  09  00  00  39  00  0d  00  00  
000c0: 01  40  00  00  00  0a  00  00  41  00  0d  00  00  01  87  00  
000d0: 00  00  0b  04  00  49  00  0d
结果如下：
00  00  00  b2（178） --> 00  00  00  08(8号页) -->下条数据位置 0d --> 00b1
00  00  00  f9（249） --> 00  00  00  09(9号页) -->下条数据位置 0d --> 00b1
00  00  01  40（320） --> 00  00  00  0a(10号页) -->下条数据位置 0d --> 00b1
00  00  01  87（391） --> 00  00  00  0b(11号页) -->下条数据位置 0d --> 00b1
```

##### 非聚集索引页

```
page offset 00000004, page type <B-tree Node>, page level <0001>
File Header Info:
FIL_PAGE_SPACE_OR_CHECKSUM           e9e852b5
FIL_PAGE_OFFSET                      00000004
FIL_PAGE_PREV                        ffffffff
FIL_PAGE_NEXT                        ffffffff
FIL_PAGE_LSN                         0000007ed13ae350
FIL_PAGE_TYPE                        45bf
FIL_PAGE_FILE_FLUSH_LSN              0000000000000000
FIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID     00000040
Data Info:
Total record size is 16 ,record position from 007f to 0188
Page Header Info :
PAGE_N_DIR_SLOTS     0004
PAGE_HEAD_TOP        0188
PAGE_N_HEAP          8012
PAGE_FREE            0000
PAGE_GARBAGE         0000
PAGE_LAST_INSER      017c
PAGE_DIRECTION       0005
PAGE_N_DIRECTION     0000
PAGE_N_RECS          0010
PAGE_MAX_TRX_ID      0000000000000000
PAGE_LEVEL           0001
PAGE_INDEX_ID        0000000000000042
PAGE_BTR_SEG_LEAF    00000040000000020272
PAGE_BTR_SEG_TOP     000000400000000201b2
Infimum Record Info:
record header : 010002001a
Value         : 696e66696d756d00
Supremum Record Info:
record header : 05000b0000
Value         : 73757072656d756d
Page Dictionary Info:
0063 00c1 016b 0070 
```

 截取槽位1的数据：

```
000c0: 66  05  ee  8b  a3  00  00  00  70  00  00  00  29  00  00  39  
000d0: 00  44  05  44  3a  ff  00  00  0d  f8  00  00  00  2a  00  00  
000e0: 41  00  88  05  fa  6a  f7  00  00  07  56  00  00  00  2b  00  
000f0: 00  49  00  66  06  1b  c8  7c  00  00  07  1c  00  00  00  2c  
00100: 00  00  51  ff  bc  05  eb  67  4c  00  00  05  ea  00  00  00  
00110: 2d  00  00  59  ff  89  05  bc  7e  a1  00  00  07  3f  00  00  
00120: 00  2e  00  00  61  ff  67  05  f1  9a  d4  00  00  1a  da  00  
00130: 00  00  2f  00  00  69  ff  9a  04  be  b4  01  00  00  02  94  
00140: 00  00  00  30  00  00  71  ff  ab  06  02  e8  5f  00  00  1d  
00150: d7  00  00  00  31  00  00  79  ff  16  06  ad  7a  f1  00  00  
00160: 17  6a  00  00  00  32  05  00  81  ff  45  05  fd  45  bf  00
结果如下：
05  ee  8b  a3（99519395） --> 主键值：00  00  00  70（112）
```

#### Page header值的验证

预置数据：

```
mysql> create table ttt(id int auto_increment primary key,k int,pad varchar(96));
Query OK, 0 rows affected (0.03 sec)

mysql> desc ttt;
+-------+-------------+------+-----+---------+----------------+
| Field | Type        | Null | Key | Default | Extra          |
+-------+-------------+------+-----+---------+----------------+
| id    | int(11)     | NO   | PRI | NULL    | auto_increment |
| k     | int(11)     | YES  |     | NULL    |                |
| pad   | varchar(96) | YES  |     | NULL    |                |
+-------+-------------+------+-----+---------+----------------+
3 rows in set (0.00 sec)

mysql> insert into ttt(k,pad) values (floor(rand() * 10000000),repeat('a',96));
Query OK, 1 row affected (0.48 sec)
mysql> insert into ttt(k,pad) values (floor(rand() * 10000000),repeat('a',96));
Query OK, 1 row affected (0.13 sec
mysql> insert into ttt(k,pad) values (floor(rand() * 10000000),repeat('a',96));
Query OK, 1 row affected (0.01 sec)
mysql> insert into ttt(k,pad) values (floor(rand() * 10000000),repeat('a',96));
Query OK, 1 row affected (0.23 sec)
mysql> insert into ttt(k,pad) values (floor(rand() * 10000000),repeat('a',96));
Query OK, 1 row affected (0.01 sec)
mysql> insert into ttt(k,pad) values (floor(rand() * 10000000),repeat('a',96));
Query OK, 1 row affected (0.01 sec)
mysql> insert into ttt(k,pad) values (floor(rand() * 10000000),repeat('a',96));
Query OK, 1 row affected (0.17 sec)
mysql> insert into ttt(k,pad) values (floor(rand() * 10000000),repeat('a',96));
Query OK, 1 row affected (0.01 sec)
mysql> select count(*) from ttt;
+----------+
| count(*) |
+----------+
|        8 |
+----------+
1 row in set (0.00 sec)
```

 解析数据页：

```
page offset 00000003, page type <B-tree Node>, page level <0000>
Data Info:
Total record size is 8 ,record position from 007f to 0458
Page Header Info :
......
PAGE_LAST_INSER      03e3 
PAGE_DIRECTION       0002
PAGE_N_DIRECTION     0007
PAGE_N_RECS          0008 
......
Infimum Record Info:
record header : 010002001c
Value         : 696e66696d756d00
Supremum Record Info:
record header : 05000b0000
Value         : 73757072656d756d
Page Dictionary Info:
0063 01f3 0070 
```

对page header字段中的解释：

| 参数名           | 值   | 解释                                                         |      |
| ---------------- | ---- | ------------------------------------------------------------ | ---- |
| PAGE_LAST_INSER  | 03e3 | 最后插入记录的位置                                           |      |
| PAGE_DIRECTION   | 0002 | 上一次插入时，cursor移动的方向，可能的值如下：#define PAGE_LEFT 1#define PAGE_RIGHT 2#define PAGE_SAME_REC 3#define PAGE_SAME_PAGE 4#define PAGE_NO_DIRECTION 5 |      |
| PAGE_N_DIRECTION | 0007 | 表示的是多次连续的INSERT，cursor在同一个方向上移动次数，插入一条为0，再插入1条增加1 |      |
| PAGE_N_RECS      | 0008 | 记录总数                                                     |      |

-  执行一次删除delete from ttt where id =5：


```
PAGE_LAST_INSER      0000
PAGE_DIRECTION       0002
PAGE_N_DIRECTION     0008
PAGE_N_RECS          0008
-- 上一次插入位置的指针，如果上一次对PAGE的操作是DELETE，则这个值会被重置成NULL。
```

- 再次执行一次插入insert into ttt(k,pad) values (floor(rand() * 10000000),repeat('a',96))：

```
PAGE_LAST_INSER      026f
PAGE_DIRECTION       0005
PAGE_N_DIRECTION     0000
PAGE_N_RECS          0009
```

- 再次执行一次插入insert into ttt(k,pad) values (floor(rand() * 10000000),repeat('a',96))：


```
PAGE_LAST_INSER      04db
PAGE_DIRECTION       0002
PAGE_N_DIRECTION     0001
PAGE_N_RECS          000a
```

- 插入一个不连续的值insert into ttt(id,k,pad) values (99,floor(rand() * 10000000),repeat('a',96))：


```
PAGE_LAST_INSER      0557
PAGE_DIRECTION       0002
PAGE_N_DIRECTION     0002
PAGE_N_RECS          000b
```

- 插入一个小于最大自增列的值insert into ttt(id,k,pad) values (55,floor(rand() * 10000000),repeat('a',96))：


```
PAGE_LAST_INSER      05d3
PAGE_DIRECTION       0005
PAGE_N_DIRECTION     0000
PAGE_N_RECS          000c
```

#### 页面分裂验证

为了验证分裂，一个页面保存4-5条数据，创建表：

```mysql
mysql> create table d(id int primary key,col varchar(1500));
Query OK, 0 rows affected (0.04 sec)

mysql> desc d;
+-------+---------------+------+-----+---------+-------+
| Field | Type          | Null | Key | Default | Extra |
+-------+---------------+------+-----+---------+-------+
| id    | int(11)       | NO   | PRI | NULL    |       |
| col   | varchar(1500) | YES  |     | NULL    |       |
+-------+---------------+------+-----+---------+-------+
2 rows in set (0.00 sec)
-- 一条数据的长度为1504，一个页面长度为16000，保存数据9条
```

##### 数据连续插入

```
page offset 00000003, page type <B-tree Node>, page level <0001>
File Header Info:
FIL_PAGE_SPACE_OR_CHECKSUM           c761f361
FIL_PAGE_OFFSET                      00000003
FIL_PAGE_PREV                        ffffffff
FIL_PAGE_NEXT                        ffffffff
FIL_PAGE_LSN                         00000000076b0d79
FIL_PAGE_TYPE                        45bf
FIL_PAGE_FILE_FLUSH_LSN              0000000000000000
FIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID     000001ee
Data Info:
Total record size is 4 ,record position from 007f to 00b0
Page Header Info :
PAGE_N_DIR_SLOTS     0002
PAGE_HEAD_TOP        00b0
PAGE_N_HEAP          8006
PAGE_FREE            0000
PAGE_GARBAGE         0000
PAGE_LAST_INSER      00a8
PAGE_DIRECTION       0002
PAGE_N_DIRECTION     0003
PAGE_N_RECS          0004
PAGE_MAX_TRX_ID      0000000000000000
PAGE_LEVEL           0001
PAGE_INDEX_ID        0000000000000205
PAGE_BTR_SEG_LEAF    000001ee0000000200f2
PAGE_BTR_SEG_TOP     000001ee000000020032
Infimum Record Info:
record header : 010002001b
Value         : 696e66696d756d00
Supremum Record Info:
record header : 05000b0000
Value         : 73757072656d756d
Page Dictionary Info:
[0063, 0070]
Primary Key Info:
[80000001, 80000005, 8000000e, 80000017]
page offset 00000004, page type <B-tree Node>, page level <0000>
File Header Info:
FIL_PAGE_SPACE_OR_CHECKSUM           52443003
FIL_PAGE_OFFSET                      00000004
FIL_PAGE_PREV                        ffffffff
FIL_PAGE_NEXT                        00000005
FIL_PAGE_LSN                         00000000076abb61
FIL_PAGE_TYPE                        45bf
FIL_PAGE_FILE_FLUSH_LSN              0000000000000000
FIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID     000001ee
Data Info:
Total record size is 4 ,record position from 007f to 3615
Page Header Info :
PAGE_N_DIR_SLOTS     0003
PAGE_HEAD_TOP        3615
PAGE_N_HEAP          800b
PAGE_FREE            1854
PAGE_GARBAGE         1dc9
PAGE_LAST_INSER      0000
PAGE_DIRECTION       0005
PAGE_N_DIRECTION     0000
PAGE_N_RECS          0004
PAGE_MAX_TRX_ID      0000000000000000
PAGE_LEVEL           0000
PAGE_INDEX_ID        0000000000000205
PAGE_BTR_SEG_LEAF    00000000000000000000
PAGE_BTR_SEG_TOP     00000000000000000000
Infimum Record Info:
record header : 010002001d
Value         : 696e66696d756d00
Supremum Record Info:
record header : 01000b0000
Value         : 73757072656d756d
Page Dictionary Info:
[0063, 125f, 0070]
Primary Key Info:
[80000001, 80000002, 80000003, 80000004]
page offset 00000005, page type <B-tree Node>, page level <0000>
File Header Info:
FIL_PAGE_SPACE_OR_CHECKSUM           33cf409b
FIL_PAGE_OFFSET                      00000005
FIL_PAGE_PREV                        00000004
FIL_PAGE_NEXT                        00000006
FIL_PAGE_LSN                         00000000076b0d79
FIL_PAGE_TYPE                        45bf
FIL_PAGE_FILE_FLUSH_LSN              0000000000000000
FIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID     000001ee
Data Info:
Total record size is 9 ,record position from 007f to 3615
Page Header Info :
PAGE_N_DIR_SLOTS     0003
PAGE_HEAD_TOP        3615
PAGE_N_HEAP          800b
PAGE_FREE            0000
PAGE_GARBAGE         0000
PAGE_LAST_INSER      3028
PAGE_DIRECTION       0002
PAGE_N_DIRECTION     0003
PAGE_N_RECS          0009
PAGE_MAX_TRX_ID      0000000000000000
PAGE_LEVEL           0000
PAGE_INDEX_ID        0000000000000205
PAGE_BTR_SEG_LEAF    00000000000000000000
PAGE_BTR_SEG_TOP     00000000000000000000
Infimum Record Info:
record header : 010002001d
Value         : 696e66696d756d00
Supremum Record Info:
record header : 06000b0000
Value         : 73757072656d756d
Page Dictionary Info:
[0063, 125f, 0070]
Primary Key Info:
[80000005, 80000006, 80000007, 80000008, 80000009, 8000000a, 8000000b, 8000000c, 8000000d]
page offset 00000006, page type <B-tree Node>, page level <0000>
File Header Info:
FIL_PAGE_SPACE_OR_CHECKSUM           5a8f0fbc
FIL_PAGE_OFFSET                      00000006
FIL_PAGE_PREV                        00000005
FIL_PAGE_NEXT                        00000007
FIL_PAGE_LSN                         00000000076b0d79
FIL_PAGE_TYPE                        45bf
FIL_PAGE_FILE_FLUSH_LSN              0000000000000000
FIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID     000001ee
Data Info:
Total record size is 9 ,record position from 007f to 3615
Page Header Info :
PAGE_N_DIR_SLOTS     0003
PAGE_HEAD_TOP        3615
PAGE_N_HEAP          800b
PAGE_FREE            0000
PAGE_GARBAGE         0000
PAGE_LAST_INSER      3028
PAGE_DIRECTION       0002
PAGE_N_DIRECTION     0008
PAGE_N_RECS          0009
PAGE_MAX_TRX_ID      0000000000000000
PAGE_LEVEL           0000
PAGE_INDEX_ID        0000000000000205
PAGE_BTR_SEG_LEAF    00000000000000000000
PAGE_BTR_SEG_TOP     00000000000000000000
Infimum Record Info:
record header : 010002001d
Value         : 696e66696d756d00
Supremum Record Info:
record header : 06000b0000
Value         : 73757072656d756d
Page Dictionary Info:
[0063, 125f, 0070]
Primary Key Info:
[8000000e, 8000000f, 80000010, 80000011, 80000012, 80000013, 80000014, 80000015, 80000016]
page offset 00000007, page type <B-tree Node>, page level <0000>
File Header Info:
FIL_PAGE_SPACE_OR_CHECKSUM           5a838c84
FIL_PAGE_OFFSET                      00000007
FIL_PAGE_PREV                        00000006
FIL_PAGE_NEXT                        ffffffff
FIL_PAGE_LSN                         00000000076b0d79
FIL_PAGE_TYPE                        45bf
FIL_PAGE_FILE_FLUSH_LSN              0000000000000000
FIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID     000001ee
Data Info:
Total record size is 1 ,record position from 007f to 066d
Page Header Info :
PAGE_N_DIR_SLOTS     0002
PAGE_HEAD_TOP        066d
PAGE_N_HEAP          8003
PAGE_FREE            0000
PAGE_GARBAGE         0000
PAGE_LAST_INSER      0080
PAGE_DIRECTION       0005
PAGE_N_DIRECTION     0000
PAGE_N_RECS          0001
PAGE_MAX_TRX_ID      0000000000000000
PAGE_LEVEL           0000
PAGE_INDEX_ID        0000000000000205
PAGE_BTR_SEG_LEAF    00000000000000000000
PAGE_BTR_SEG_TOP     00000000000000000000
Infimum Record Info:
record header : 010002001d
Value         : 696e66696d756d00
Supremum Recod Info:
record header : 02000b0000
Value         : 73757072656d756d
Page Dictionary Info:
[0063, 0070]
Primary Key Info:
[80000017]
Total number of page:9
Insert Buffer Bitmap:1
File Segment inode:1
B-tree Node:5
File Space Header:1
Freshly Allocated Page:1
```

上例模拟了多次拆分后的结果：

1. 一个页面最多保存9条记录

2. 顺序插入的时候第1个页拆分的时候使用的是二分拆。

3. 从第2页开始，再分裂的时候，采用的就是直接在后面插入，判断条件是：

   ```
   if (page_is_leaf(page)   叶子节点
    && (mode == PAGE_CUR_LE) 
    && !dict_index_is_spatial(index) 非空间索引
    && (page_header_get_field(page, PAGE_N_DIRECTION) > 3) 连续插入3个值
    && (page_header_get_ptr(page, PAGE_LAST_INSERT)) 
    && (page_header_get_field(page, PAGE_DIRECTION) == PAGE_RIGHT)) 插入方向为向右
   ```

   \#define    PAGE_CUR_G          1        >查询

   \#define    PAGE_CUR_GE         2        >=，=查询

   \#define    PAGE_CUR_L          3        <查询

   \#define    PAGE_CUR_LE         4        <=查询

   然后根据这四种不同的Search Mode，在二分查找碰到相同键值时进行调整。例如：若Search Mode为PAGE_CUR_G或者是PAGE_CUR_LE，则移动low至mid，继续进行二分查找；若Search Mode为PAGE_CUR_GE或者是PAGE_CUR_L，则移动high至mid，继续进行二分查找。

4. 通过表信息查看每个页面的数据个数：

   ```
   mysql> select page_number, page_type, number_records, data_size from information_schema.INNODB_BUFFER_PAGE where table_n
   ame ='`lt`.`d`' and index_name='PRIMARY';
   +-------------+-----------+----------------+-----------+
   | page_number | page_type | number_records | data_size |
   +-------------+-----------+----------------+-----------+
   |           3 | INDEX     |              4 |        56 |
   |           4 | INDEX     |              4 |      6100 |
   |           5 | INDEX     |              9 |     13725 |
   |           6 | INDEX     |              9 |     13725 |
   |           7 | INDEX     |              1 |      1525 |
   +-------------+-----------+----------------+-----------+
   5 rows in set (0.05 sec)
   ```

### 代码地址

https://github.com/qiyibaba/qiyibaba.github.io/blob/master/code/IbdFileParser.java

### 参考资料

《MySQL技术内幕 InnoDB存储引擎》

 
