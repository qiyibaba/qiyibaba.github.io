---
layout:     post                    
title:     	innodb体系架构									
subtitle:   MySQL技术内幕 InnoDB存储引擎体系架构
date:       2020-02-29            
author:     Qiyibaba               
header-img: img/post-bg-202002.jpeg   
catalog: true                     
tags:                               
    - mysql
    - innodb
---

### 结构

innodb的整个体系架构就是由多个内存块组成的缓冲池及多个后台线程构成。缓冲池缓存磁盘数据（解决cpu速度和磁盘速度的严重不匹配问题），后台进程保证缓存池和磁盘数据的一致性（读取、刷新），并保证数据异常宕机时能恢复到正常状态。

缓冲池主要分为三个部分：redo *log buffer、innodb_buffer_pool、innodb_additional_mem_pool。*

- innodb_buffer_pool由包含数据、索引、insert buffer ,adaptive hash index,lock 信息及数据字典。
- redo log buffer用来缓存重做日志。
- additional memory pool:用来缓存LRU链表、等待、锁等数据结构。

后台进程分为：master thread，IO thread，purge thread，page cleaner thread。

- master thread负责刷新缓存数据到磁盘并协调调度其它后台进程。
- IO thread 分为 insert buffer、log、read、write进程。分别用来处理insert buffer、重做日志、读写请求的IO回调。
- purge thread用来回收undo 页
- page cleaner thread用来刷新脏页。

### 后台线程

#### Master Thread

Master Thread是一个核心的后台线程，主要负责将缓冲池中的数据异步刷新到磁盘，保证数据的一致性，包括脏页的刷新，合并插入缓冲，undo页的回收。

##### InnoDB1.0X版本之前的Master Thread

Master Thread具有最高的线程优先级别，内部由多个循环组成：主循环（loop）、后台循环（background loop）、刷新循环（flush loop）、暂停循环（suspend loop），Master Thread会根据数据库运行的状态进行循环之间的切换。

Loop主循环（大多数操作都在这个循环下）这个由两大部分操作，每秒和每10秒操作：

```
void master_thread() {
    loop:
    for(int i=0; i<10; i++) {
        do thing once per second
        sleep 1 second if necessary
    }
    do things once per then seconds
    goto loop;
}
```

可以发现，loop循环是通过thread sleep来实现的，意味着每秒或者每10每秒的操作并不是十分的精确的，在负载大的情况下，可能会有不同程度的延迟（delay）。

每秒一次的操作包括：

1. 日志缓冲刷新到磁盘（总是）：即使事务没有提交，InnoDB也会每秒将重做日志缓冲刷新到重做日志文件中，因此可以理解为什么再大的事务提交，时间也是很短的。
2. 合并插入缓冲insert buffer（可能）：并不是每秒刷新的，如果前一秒的IO次数小于5，则认为IO压力小，可以执行合并插入缓冲的操作。
3. 最多刷新100个InnoDB的缓冲池脏页到磁盘（可能）：判断当前缓冲池中脏页的比例(buf_get_modifyed_ratio_pct)是否超过了配置文件中innodb_max_dirty_pages_pct这个参数（默认为90）如果超过了这个阈值，InnoDB存储引擎认为需要做同步操作，将100个脏页写入磁盘中。
4. 如果当前没有用户活动，切换到background loop（可能）

每10秒的操作：

1. 刷新100个脏页到磁盘(可能)
2. 合并至多5个插入缓冲(总是)
3. 将日志缓冲刷新到磁盘(总是)
4. 删除无用的undo页(总是)：InnoDB存储引擎会执行full purse操作，即删除无用的Undo页，对表进行update、delete这类操作，原先行被标记删除，但是因为一致性读读关系，需要保留这些行的版本号，这时候会进行回收删除。
5. 刷新100个或者10个脏页到磁盘(总是)

接着来看**background loop** 若当前没有用户活动（数据库空闲时）或者数据库关闭（shutdown），就会切换到这个循环执行以下操作：

1.  删除无用的undo页(总是)
2. 合并20个插入缓冲(总是)
3. 跳回到主循环(总是)
4. 不断刷新100个页,直到符合条件(可能,跳转到flush loop中完成)：如果fulsh loop 页没有什么事情可以做了，InnoDB存储引擎会切换到suspend loop，将Master Thread刮起。

##### InnoDB1.2.X之前的版本的Master Thread

在如今磁盘技术的快速发展中，对于缓冲池向磁盘刷新时都做了一定的hard coding，这些限制很大程度上限制了InnoDB存储引擎对磁盘IO的性能，尤其是写入性能。

因此提供参数innodb_io_capacity用来表示IO的吞吐量，默认200，对于刷新到磁盘页的数量，会按照innodb_io_capacity的百分比来控制：

- 在合并插入缓冲时，合并插入缓冲的数量为innodb_io_capacity值5%;
- 在从缓冲池刷新脏页时，刷行脏页的数量为innodb_io_capcity;

通过以下为代码，我们可以得到InnoDB1.2X前Master Thread的工作方式：

```
void master_thread() {
    loop:
    for(int i=0; i<10; i++) {
        thread_sleep(1)    // sleep 1秒
        do log buffer flush to dish

        if (last_one_second_ios < 5% innodb_io_capacity) {
            do merget 5% innodb_io_capacity insert buffer
        }

        if (buf_get_modified_ratio_pct > innodb_max_dirty_pages_pct) {  // 如果缓冲池中的脏页比例大于innodb_max_dirty_pages_pct(默认是75时)
            do buffer pool flush 100% innodb_io_capacity dirty page  // 刷新全部脏页到磁盘
        } else if (enable adaptive flush) {    // 如果开户了自适应刷新
            do buffer pool flush desired amount dirty page // 通过判断产生redo log的速度决定最合适的刷新脏页的数量
        }

        if (no user activetuy) {
            goto background loop
        }
    }

    if (last_ten_second_ios < innodb_io_capacity) {  // 如果过去10内磁盘IO次数小于设置的innodb_io_capacity的值（默认是200）
        do buffer pool flush 100%  innodb_io_capacity dirty page
    }

    do merge 5% innodb_io_capacity insert buffer  // 合并插入缓冲是innodb_io_capacity的5%（10）（总是）
    do log buffer flush to dish
    do flush purge

    if (buf_get_modified_ratio_pct > 70%) {
        do buffer pool flush 100% innodb_io_capacity dirty page
    } else {
        do buffer pool flush 10% innodb_io_capacity dirty page
    }
    goto loop

    backgroud loop:   // 后台循环
    do full purge     // 删除无用的undo页 （总是）
    do merger 5% innodb_io_capacity insert buffer  // 合并插入缓冲是innodb_io_capacity的5%（10）（总是）
    if not idle:      // 如果不空闲，就跳回主循环，如果空闲就跳入flush loop
    goto loop:    // 跳到主循环
    else:
        goto flush loop
    flush loop:  // 刷新循环
    do buf_get_modified_ratio_pct pool flush 100% innodb_io_capacity dirty page // 刷新200个脏页到磁盘
    if ( buf_get_modified_ratio_pct > innodb_max_dirty_pages_pct ) // 如果缓冲池中的脏页比例大于innodb_max_dirty_pages_pct的值（默认75%）
        goto flush loop            // 跳到刷新循环，不断刷新脏页，直到符合条件
        goto suspend loop          // 完成刷新脏页的任务后，跳入suspend loop
    suspend loop:
    suspend_thread()               //master线程挂起，等待事件发生
    waiting event
    goto loop;
}
```

##### InnoDB1.2.x版本的Master Thread

```
if (InnoDB is idle) {
    srv_master_do_idle_tasks();    // 每10秒操作
} else {
    srv_master_do_active_tasks();    // 每秒操作
}
```

#### IO Thread

在InnoDb存储引擎中大量使用Async IO来处理IO的请求，可以极大提高数据库的性能。而IO Thread的主要工作是负责IO请求的回调处理。较早之前版本有4个IO Thread 分别是write , read , insert buffer,log IO Thread

```
--------
FILE I/O
--------
I/O thread 0 state: waiting for completed aio requests (insert buffer thread)
I/O thread 1 state: waiting for completed aio requests (log thread)
I/O thread 2 state: waiting for completed aio requests (read thread)
...共16个
I/O thread 18 state: waiting for completed aio requests (write thread)
...共16个

线程控制方式：
mysql> show variables like '%io_threads%';
+-------------------------+-------+
| Variable_name           | Value |
+-------------------------+-------+
| innodb_read_io_threads  | 16    |
| innodb_write_io_threads | 16    |
+-------------------------+-------+
2 rows in set (0.00 sec)
```

#### Purge Thread

事务被提交后，其所使用的undo log可能不再需要，因此需要Purge Thread来及时回收已经分配的undo页。

```
mysql> show variables like '%purge_threads%';  
+----------------------+-------+
| Variable_name        | Value |
+----------------------+-------+
| innodb_purge_threads | 4     |
+----------------------+-------+
1 row in set (0.00 sec)
```



Page Cleaner Thread为高版本InnoDB引擎引入，其作用是将之前版本的脏页刷新操作放入单独的线程来完成。其目的为了减轻Master Thread的负担，从而进一步提高InnoDB存储引擎的性能

### 内存（buffer）

#### Innodb buffer pool

```
mysql> show variables like '%innodb_buffer_pool%';
+-------------------------------------+----------------+
| Variable_name                       | Value          |
+-------------------------------------+----------------+
| innodb_buffer_pool_instances        | 8              |
| innodb_buffer_pool_size             | 17179869184    |
+-------------------------------------+----------------+
```

如参数列表中展示，16G的buffer pool被分成了8个实例，每个实例的大小为2G，需要查看每个实例的buffer  pool使用情况，可以通过show engine innodb status查看：

```
----------------------
INDIVIDUAL BUFFER POOL INFO
----------------------
---BUFFER POOL 0 --标记（0-7）
Buffer pool size   131072 --大小，此处统计的是页，每个页大小为16K，则总大小为2G
Free buffers       44109 --FREE列表中的页数量
Database pages     79650 --LRU列表中的页数量
Old database pages 29382
Modified db pages  49525
Pending reads      0
Pending writes: LRU 0, flush list 0, single page 0
Pages made young 0, not young 0 
0.00 youngs/s, 0.00 non-youngs/s
Pages read 76764, created 2886, written 2897291
0.00 reads/s, 0.00 creates/s, 544.74 writes/s
Buffer pool hit rate 1000 / 1000, young-making rate 0 / 1000 not 0 / 1000 --缓冲池的命中率
Pages read ahead 0.00/s, evicted without access 0.00/s, Random read ahead 0.00/s
LRU len: 79650, unzip_LRU len: 0
I/O sum[0]:cur[4730], unzip sum[0]:cur[0]
```

还可以通过表information_schema.innodb_buffer_pool_stats查看：

```
mysql> select pool_id,pool_size from information_schema.innodb_buffer_pool_stats;
+---------+-----------+
| pool_id | pool_size |
+---------+-----------+
|       0 |    131072 |
|       1 |    131072 |
|       2 |    131072 |
|       3 |    131072 |
|       4 |    131072 |
|       5 |    131072 |
|       6 |    131072 |
|       7 |    131072 |
+---------+-----------+
8 rows in set (0.01 sec)
```

####  LRU List(Latest Recent Used)

默认大小页的大小16KB，通过show engine innodb status;可以查看当前缓冲池的页数。InnoDB对传统的LRU算法进行了优化。在InnoDB中加入了midpoint。传统的LRU算法当访问到的页不在缓冲区是直接将磁盘页数据调到缓冲区队列；而InnoDB并不是直接插入到缓冲区队列的队头，而是插入LRU列表的midpoint位置。这个算法称之为midpoint insertion stategy。默认配置插入到列表长度的5/8处。midpoint由参数innodb_old_blocks_pct控制：

```
mysql> show variables like 'innodb_old_blocks_pct';
+-----------------------+-------+
| Variable_name         | Value |
+-----------------------+-------+
| innodb_old_blocks_pct | 37    |
+-----------------------+-------+
1 row in set (0.00 sec)
```

midpoint之前的列表称之为new列表，之后的列表称之为old列表。可以简单的将new列表中的页理解为最为活跃的热点数据。

 好处：不使用朴素的LRU算法。出于效率考虑，因为可能存在类似于“扫表”等偶然操作，这样做可以避免将热点数据替换掉，而添加到缓冲区的页是偶然操作用到的页。

然而mid位置的页不是永久的。为了解决这个问题，InnoDB存储引擎引入了innodb_old_blocks_time来表示页读取到mid位置之后需要等待多久才会被加入到LRU列表的热端。可以通过设置该参数保证热点数据不轻易被刷出。

```
mysql> show variables like 'innodb_old_blocks_time';
+------------------------+-------+
| Variable_name          | Value |
+------------------------+-------+
| innodb_old_blocks_time | 1000  |
+------------------------+-------+
1 row in set (0.00 sec)
```

-  Free List


数据库刚启动的时候，LRU 列表为空，此时需要用到的时候直接将Free列表中的页删除，在LRU列表中增加相应的页，维持页数守恒。

-  Flush List　　


 当LRU列表中的页被修改后，称该页为脏页（dirty page），即缓冲池中的页和磁盘上的页数据产生了不一致。这时候数据库会通过checkpoint机制将脏页刷新回磁盘，而Flush 列表中的页即为脏页列表。注意脏页也存在于ＬＲＵ列表中。

#### INNODB_BUFFER_PAGE_LRU 介绍

INNODB_BUFFER_PAGE_LRU 表存在于INFORMATION_SCHEMA中，它记录了InnoDB buffer pool中所有pages的信息，特别是当buffer pool满了之后，LRU列表决定了按照顺序驱逐pages。

> 警告！！查询INNODB_BUFFER_PAGE_LRU表会引起显著的性能压力，千万不要在生产环境查询该表。
>

| **Column name**     | **Description**                                              |
| ------------------- | ------------------------------------------------------------ |
| POOL_ID             | Buffer Pool ID. An identifier to distinguish between multiple buffer pool instances. 多个buffer pool时，page所在buffer pool 的id。 |
| LRU_POSITION        | The position of the page in the LRU list. 该page在LRU列表中的位置。 |
| SPACE               | Tablespace ID. Uses the same value as in INNODB_SYS_TABLES.SPACE. MySQL InnoDB buffer pool预热机制保存数据时，需要的tablespace id就是获取的这列。 |
| PAGE_NUMBER         | Page number.                                                 |
| PAGE_TYPE           | Page type. Permitted values are ALLOCATED (Freshly allocated page), INDEX (B-tree node), UNDO_LOG (Undo log page), INODE(Index node), IBUF_FREE_LIST (Insert buffer free list), IBUF_BITMAP (Insert buffer bitmap), SYSTEM (System page), TRX_SYSTEM(Transaction system data), FILE_SPACE_HEADER (File space header), EXTENT_DESCRIPTOR (Extent descriptor page), BLOB(Uncompressed BLOB page), COMPRESSED_BLOB (First compressed BLOB page), COMPRESSED_BLOB2 (Subsequent comp BLOB page), IBUF_INDEX (Insert buffer index), UNKNOWN (unknown). Page的类型，如INDEX、UNDO_LOG、system page、TRX_SYSTEM等类型。 |
| FLUSH_TYPE          | Flush type.                                                  |
| FIX_COUNT           | Number of threads using this block within the buffer pool. When zero, the block is eligible to be evicted. thread从buffer pool获取的block数量。 |
| IS_HASHED           | Whether hash index has been built on this page.              |
| NEWEST_MODIFICATION | Log Sequence Number of the youngest modification.            |
| OLDEST_MODIFICATION | Log Sequence Number of the oldest modification.              |
| ACCESS_TIME         | An abstract number used to judge the first access time of the page. |
| TABLE_NAME          | Name of the table the page belongs to. This column is only applicable to pages of type INDEX. page所属的表名。 |
| INDEX_NAME          | Name of the index the page belongs to. It can be the name of a clustered index or a secondary index. This column is only applicable to pages of type INDEX. page所属的索引名。 |
| NUMBER_RECORDS      | Number of records within the page. page中含有的记录数量。    |
| DATA_SIZE           | Sum of the sizes of the records. This column is only applicable to pages of type INDEX. |
| COMPRESSED_SIZE     | Compressed page size. Null for pages that are not compressed. |
| PAGE_STATE          | Page state. A page with valid data has one of the following states: FILE_PAGE (buffers a page of data from a file), MEMORY(buffers a page from an in-memory object), COMPRESSED. Other possible states (managed by InnoDB) are: NULL,READY_FOR_USE, NOT_USED, REMOVE_HASH. |
| IO_FIX              | Specifies whether any I/O is pending for this page: IO_NONE = no pending I/O, IO_READ = read pending, IO_WRITE = write pending. |
| IS_OLD              | Specifies whether or not the block is in the sublist of old blocks in the LRU list. |
| FREE_PAGE_CLOCK     | The value of the freed_page_clock counter when the block was the last placed at the head of the LRU list. Thefreed_page_clock counter tracks the number of blocks removed from the end of the LRU list. |

MySQL查询示例：

```
mysql> SELECT * FROM INFORMATION_SCHEMA.INNODB_BUFFER_PAGE_LRU LIMIT 1\G
*************************** 1. row ***************************
            POOL_ID: 0
       LRU_POSITION: 0
              SPACE: 97
        PAGE_NUMBER: 1984
          PAGE_TYPE: INDEX
         FLUSH_TYPE: 1
          FIX_COUNT: 0
          IS_HASHED: YES
NEWEST_MODIFICATION: 719490396
OLDEST_MODIFICATION: 0
        ACCESS_TIME: 3378383796
         TABLE_NAME: `employees`.`salaries`
         INDEX_NAME: PRIMARY
     NUMBER_RECORDS: 468
          DATA_SIZE: 14976
    COMPRESSED_SIZE: 0
         COMPRESSED: NO
             IO_FIX: IO_NONE
             IS_OLD: YES
    FREE_PAGE_CLOCK: 0
```

说明:

- 查询该表必须有PROCESS权限；
- 使用DESC 、SHOW COLUMNS命令去查看列和数据类型的信息；
- 查询INNODB_BUFFER_PAGE_LRU表需要分配连续的内存，特别是InnoDB buffer pool上G容量的时候，可能会导致OOM（out-of-memory）；
- 查询INNODB_BUFFER_PAGE_LRU表会锁定LRU列中的数据结构，特别是InnoDB buffer pool上G容量的时候，会导致并发性下降。
- 删除表、表数据、分区或者索引，这些被删除的对象的page并不会马上从buffer pool中清除，直到空间紧张，才把它们删除。

 查看缓冲池中LRU列表中每个页的具体信息：

```
mysql> select table_name,page_type,count(*) from information_schema.innodb_buffer_page_lru where pool_id=1 group by page_type,table_name;
+------------------------------+-------------------+----------+
| table_name                   | page_type         | count(*) |
+------------------------------+-------------------+----------+
| NULL                         | EXTENT_DESCRIPTOR |       11 |
| NULL                         | FILE_SPACE_HEADER |        1 |
| NULL                         | IBUF_BITMAP       |       13 |
| `mysql`.`innodb_table_stats` | INDEX             |        1 |
| `SYS_TABLES`                 | INDEX             |     2880 |
| `t_1000w`.`sbtest1`          | INDEX             |    19332 |
| `t_3000w`.`sbtest1`          | INDEX             |    57289 |
| NULL                         | INODE             |        2 |
| NULL                         | SYSTEM            |       24 |
| NULL                         | UNDO_LOG          |      718 |
+------------------------------+-------------------+----------+
10 rows in set (2.08 sec)
```



参考资料：https://www.cnblogs.com/wilburxu/p/8644939.html

参考资料：《MySQL技术内幕 InnoDB存储引擎》
