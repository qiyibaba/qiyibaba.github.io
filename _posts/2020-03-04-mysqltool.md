---
layout:     post                    
title:     	MySQL性能监控的2个工具介绍										
subtitle:   tuning-primer.sh， ./mysqltuner.pl工具及使用介绍
date:       2020-03-04            
author:     Qiyibaba               
header-img: img/post-bg-202003.jpg   
catalog: true                     
tags:                               
    - mysql
---

## tuning-primer.sh

### 使用说明

```shell
[ltdb1@db02 ~]$ ./tuning-primer.sh all #检查所有项

Using login values from ~/.my.cnf
- INITIAL LOGIN ATTEMPT FAILED -
Testing for stored webmin passwords:
 None Found
Could not auto detect login info!
Found potential sockets: /home/bugmanagerdb/bin/mysql1.sock
/home/data_sas/ltdb1/bin/mysql1.sock
/var/lib/mysql/mysql.sock
/home/xtygdb/bin/mysql1.sock
/home/data_sas/kzxdb/bin/mysql1.sock
Using: /var/lib/mysql/mysql.sock
Would you like to provide a different socket?: [y/N] y #选择合适的socket
Socket: /home/data_sas/ltdb1/bin/mysql1.sock
Do you have your login handy ? [y/N] : y #使用账号密码
User: root
Password:
Would you like me to create a ~/.my.cnf file for you?  If you answer 'N',
then I'll create a secure, temporary one instead.  [y/N] : N #是否生成my.cnf
......
```

ALL共检测：banner_info，misc，memory，file

### 显示说明

| **SLOW QUERIES**                                             | **慢查询检查**                                               |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| SLOW QUERIESThe slow query log is enabled.                   | 说明我已经启用了慢查询记录功能。也就是参数 slow_query_log = 1 |
| Current long_query_time = 5.000000 sec.                      | 慢查询的阀值时间。也就是参数 long_query_time = 5             |
| You have 17 out of 638844 that take longer than 5.000000 sec. to complete | 说明慢查询日志中记录了17条查询时间超过5秒的语句。 slow_query_log_file=/data/ats_db/mysql-slow.log设置慢查询日志路径。使用mysqldumpslow命令查询慢日志 |
| **Your long_query_time seems to be fine**                    | 慢查询阀值时间设置得在推荐的范围内                           |
|                                                              |                                                              |
| **BINARY UPDATE LOG**                                        | **更新二进制日志文件**                                       |
| The binary update log is enabled                             | 这项说明启用了bin-log日志功能。参数 log-bin = /data/ats_db/mysql-bin |
| **Binlog sync is not enabled, you could loose binlog records during a server crash** | 没有启用 sync_binlog 选项。也即是将二进制日志实时写入到磁盘通过 sync_binlog=1来指定 |
|                                                              |                                                              |
| **WORKER THREADS**                                           | **工作线程**                                                 |
| Current thread_cache_size = 8                                | 当前线程缓存大小。 thread_concurrency = 8                    |
| Current threads_cached = 7                                   | Show status like ‘threads_cached’                            |
| Current threads_per_sec = 0                                  | 脚本先执行Show status like ‘Threads_cached’查看当前的线程创建情况，然后sleep 1后在执行相同的命令，最终后者减去前者的数就是每秒线程创建数。 |
| Historic threads_per_sec = 0                                 | 该值是使用Threads_cached /uptime获得的。                     |
| **Your thread_cache_size is fine**                           |                                                              |
|                                                              |                                                              |
| **MAX CONNECTIONS**                                          | **最大连接数**                                               |
| Current max_connections = 1024                               | 当前配置文件中设置的并发连接数                               |
| Current threads_connected = 2                                | 当前线程连接诶数。 show status like ‘Threads_connected’      |
| Historic max_used_connections = 4                            | show status like ‘Max_used_connections’;                     |
| The number of used connections is 0% of the configured maximum. | 这个值使用 Max_used_connections*100/ max_connections得出。   |
| **You are using less than 10% of your configured max_connections. Lowering max_connections could help to avoid an over-allocation of memory****See “MEMORY USAGE” section to make sure you are not over-allocating** | Max_used_connections的值不足max_connections值的10%。设置合适的max_connections值有助于节省内存。 |
|                                                              |                                                              |
| **MEMORY USAGE**                                             | **内存使用**                                                 |
| Max Memory Ever Allocated : 841 M                            | **Max Memory Ever Allocated** = **max_memory**               |
| Configured Max Per-thread Buffers : 28.40 G                  | **Configured Max Per-thread Buffers** = **per_thread_buffers** |
| Configured Max Global Buffers : 586 M                        | **Configured Max Global Buffers** = **per_thread_max_buffers** |
| Configured Max Memory Limit : 28.97 G                        | **Configured Max Memory Limit** = **total_memory** 这 一项很重要，他是将各个缓存的大小累加，然后同max_connections相乘，从而得出当达到max_connections后需要分配的内存有多 少。我这里由于max_connections写得很大，造成了最大内存限制超过了真实内存很多，所以建议不要随意增大max_connections的 值。减小 max_connections的值，最终保证最大内存限制在真实内存的90%以下。 |
| Physical Memory : 7.79 G                                     | 实际物理内存                                                 |
| Max memory limit exceeds 90% of physical memory              |                                                              |
| **per_thread_buffers** (read_buffer_size+read_rnd_buffer_size +sort_buffer_size+thread_stack+ join_buffer_size+binlog_cache_size)*max_connections**per_thread_max_buffers** (read_buffer_size+read_rnd_buffer_size +sort_buffer_size+thread_stack +join_buffer_size+binlog_cache_size)*max_used_connections**global_buffers** innodb_buffer_pool_size+innodb_additional_mem_pool_size+innodb_log_buffer_size+ key_buffer_size+query_cache_size**max_memory**=global_buffers+per_thread_max_buffers**total_memory**=global_buffers+per_thread_buffers |                                                              |
|                                                              |                                                              |
| **KEY BUFFER**                                               | **Key 缓冲**                                                 |
| Current MyISAM index space = 222 K                           | 当前数据库MyISAM表中索引占用磁盘空间                         |
| Current key_buffer_size = 512 M                              | MySQL配置文件中key_buffer_size 设置的大小                    |
| Key cache miss rate is 1 : 3316                              | Key_read_requests/ Key_reads 这里说明3316次读取请求中有1次丢失(也就是说1次读取磁盘) |
| Key buffer free ratio = 81 %                                 | key_blocks_unused * key_cache_block_size / key_buffer_size * 100 |
| **Your key_buffer_size seems to be fine**                    |                                                              |
|                                                              |                                                              |
| **QUERY CACHE**                                              | **Query 缓存**                                               |
| **Query cache is enabled**                                   | 该项说明 我们指定了query_cache_size 的值。如果query_cache_size=0的话这里给出的提示是： Query cache is supported but not enabled Perhaps you should set the query_cache_size |
| Current query_cache_size = 64 M                              | 当前系统query_cache_size 值大小 [F]                          |
| Current query_cache_used = 1 M                               | **query_cache_used =query_cache_size**-**qcache_free_memory** |
| Current query_cache_limit = 128 M                            | 变量 query_cache_limit 大小                                  |
| Current Query cache Memory fill ratio = 1.79 %               | **query_cache_used/**query_cache_size *100%                  |
| Current query_cache_min_res_unit = 4 K                       | **show variables like ‘query_cache_min_res_unit’;**          |
| **Your query_cache_size seems to be too high.Perhaps you can use these resources elsewhere** | 这项给出的结论是query_cache_size的值设置的有些过高。其比对标准是 “Query cache Memory fill ratio”的值如果小于<25%就会给出这个提示。可以将这些资源应用到其他的地方 |
| **MySQL won’t cache query results that are larger than query_cache_limit in size** | MySQL不会将大于query_cache_limit的查询结果进行缓存           |
| show status like ‘Qcache%’;Qcache_free_blocks        10 Qcache_free_memory        65891984 Qcache_hits            14437 Qcache_inserts            707 Qcache_lowmem_prunes    0 Qcache_not_cached        216 Qcache_queries_in_cache    540 Qcache_total_blocks        1191 |                                                              |
|                                                              |                                                              |
| **SORT OPERATIONS**                                          | **SORT 选项**                                                |
| Current sort_buffer_size = 6 M                               | show variables like ’sort_buffer%’;                          |
| Current read_rnd_buffer_size = 16 M                          | show variables like ‘read_rnd_buffer_size%’;                 |
| Sort buffer seems to be fine                                 |                                                              |
|                                                              |                                                              |
| **JOINS**                                                    | **JOINS**                                                    |
| Current join_buffer_size = 132.00 K                          | **show variables like ‘join_buffer_size%’;join_buffer_size**= **join_buffer_size**+4kb |
| You have had 6 queries where a join could not use an index properly | 这里的6是通过 **show status like ‘Select_full_join’;** 获得的 |
| You should enable “log-queries-not-using-indexes” Then look for non indexed joins in the slow query log. If you are unable to optimize your queries you may want to increase your join_buffer_size to accommodate larger joins in one pass.Note! This script will still suggest raising the join_buffer_size when ANY joins not using indexes are found. | 你需要启用 “**log-queries-not-using-indexes**” 然后在慢查询日志中看是否有取消索引的joins语句。如果不优化查询语句的话，则需要增大**join_buffer_size**。 |
|                                                              |                                                              |
| **OPEN FILES LIMIT**                                         | **文件打开数限制**                                           |
| Current open_files_limit = 1234 files                        | **show variables like ‘open_files_limit%’;**                 |
| The open_files_limit should typically be set to at least 2x-3xthat of table_cache if you have heavy MyISAM usage. | 如果系统中有很多的MyISAM类型的表，则建议将open_files_limit 设置为2X~3X的table_open_cache **show status like ‘Open_files’;open_files_ratio**= open_files*100/open_files_limit 如果**open_files_ratio** 超过75% 则需要加大open_files_limit |
| **Your open_files_limit value seems to be fine**             |                                                              |
|                                                              |                                                              |
| **TABLE CACHE**                                              | **TABLE 缓存**                                               |
| Current table_open_cache = 512 tables                        | show variables like ‘table_open_cache’;                      |
| Current table_definition_cache = 256 tables                  | show variables like ‘ table_definition_cache ‘;              |
| You have a total of 368 tables                               | SELECTCOUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE=’BASE TABLE’ |
| You have **371** open tables.                                | show status like ‘Open_tables’;                              |
| **The table_cache value seems to be fine**                   | **Open_tables** /**table_open_cache\*100%** < 95%            |
| **You should probably increase your table_definition_cache value.** | **table_cache_hit_rate** =open_tables*100/opened_tables      |
|                                                              |                                                              |
| **TEMP TABLES**                                              | **临时表**                                                   |
| Current max_heap_table_size = 16 M                           | show variables like ‘max_heap_table_size’;                   |
| Current tmp_table_size = 16 M                                | show variables like ‘tmp_table_size’;                        |
| Of 285 temp tables, 11% were created on disk                 | Created_tmp_tables=285created_tmp_disk_tables*100/ (created_tmp_tables+created_tmp_disk_tables)=11% |
| **Created disk tmp tables ratio seems fine**                 |                                                              |
|                                                              |                                                              |
| **TABLE SCANS**                                              | **扫描表**                                                   |
| Current read_buffer_size = 6 M                               | show variables like ‘read_buffer_size’;                      |
| Current table scan ratio = 9 : 1                             | **read_rnd_next** =show global status like ‘Handler_read_rnd_next’; **com_select**= show global status like ‘Com_select’; **full_table_scans**=read_rnd_next/com_select **Current table scan ratio** = full_table_scans : 1〃 如果表扫描率超过4000，说明进行了太多表扫描，很有可能索引没有建好，增加read_buffer_size值会有一些好处，但最好不要超过8MB。 |
| **read_buffer_size seems to be fine**                        |                                                              |
|                                                              |                                                              |
| **TABLE LOCKING**                                            | **TABLE LOCKING**                                            |
| Current Lock Wait ratio = 0 : 5617                           | show global status like’Table_locks_waited’; show global status like‘Questions’; 如果 Table_locks_waited=0 Current Lock Wait ratio = 0: Questions |
| **Your table locking seems to be fine**                      |                                                              |

## mysqltuner.pl

### 使用说明

```
[ltdb1@db02 ~]$ ./mysqltuner.pl
 >>  MySQLTuner 1.7.19 - Major Hayden <major@mhtx.net>
 >>  Bug reports, feature requests, and downloads at http://mysqltuner.com/
 >>  Run with '--help' for additional options and output filtering

[--] Skipped version check for MySQLTuner script
Please enter your MySQL administrative login: root
Please enter your MySQL administrative password: 
```

### 结果说明

```
[OK] Currently running supported MySQL version 5.7.17-log
[OK] Operating on 64-bit architecture

-------- Log file Recommendations ------------------------------------------------------------------
[OK] Log file /home/data_sas/ltdb1/log/mysqld1.log exists
[--] Log file: /home/data_sas/ltdb1/log/mysqld1.log(337K)
[OK] Log file /home/data_sas/ltdb1/log/mysqld1.log is readable.
[OK] Log file /home/data_sas/ltdb1/log/mysqld1.log is not empty
[OK] Log file /home/data_sas/ltdb1/log/mysqld1.log is smaller than 32 Mb
[!!] /home/data_sas/ltdb1/log/mysqld1.log contains 335 warning(s).
[!!] /home/data_sas/ltdb1/log/mysqld1.log contains 530 error(s).
[--] 22 start(s) detected in /home/data_sas/ltdb1/log/mysqld1.log
[--] 1) 2020-02-19T02:21:00.784685Z 0 [Note] /home/data_sas/ltdb1/bin/mysqld: ready for connections.
[--] 2) 2019-05-27T03:46:54.274348Z 0 [Note] /home/data_sas/ltdb1/bin/mysqld: ready for connections.
[--] 3) 2018-08-09T07:52:15.138471Z 0 [Note] /home/data_sas/ltdb1/bin/mysqld: ready for connections.
[--] 4) 2018-07-10T06:48:06.935458Z 0 [Note] /home/data_sas/ltdb1/bin/mysqld: ready for connections.
[--] 5) 2018-07-05T02:12:23.466880Z 0 [Note] /home/data_sas/ltdb1/bin/mysqld: ready for connections.
[--] 6) 2018-07-05T01:28:48.164830Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: ready for connections.
[--] 7) 2018-07-02T07:20:40.450330Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: ready for connections.
[--] 8) 2018-06-29T05:43:11.322883Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: ready for connections.
[--] 9) 2018-06-19T08:42:53.714442Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: ready for connections.
[--] 10) 2018-06-19T06:38:06.575037Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: ready for connections.
[--] 17 shutdown(s) detected in /home/data_sas/ltdb1/log/mysqld1.log
[--] 1) 2018-07-05T02:11:36.924477Z 0 [Note] /home/data_sas/ltdb1/bin/mysqld: Shutdown complete
[--] 2) 2018-07-05T02:09:27.276168Z 0 [Note] /home/data_sas/ltdb1/bin/mysqld: Shutdown complete
[--] 3) 2018-07-05T02:03:28.170671Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: Shutdown complete
[--] 4) 2018-07-04T03:11:09.223035Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: Shutdown complete
[--] 5) 2018-06-29T05:43:37.288154Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: Shutdown complete
[--] 6) 2018-06-29T05:43:06.528813Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: Shutdown complete
[--] 7) 2018-06-19T08:42:43.956635Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: Shutdown complete
[--] 8) 2018-06-19T06:37:57.127397Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: Shutdown complete
[--] 9) 2018-06-04T08:29:37.894882Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: Shutdown complete
[--] 10) 2018-05-24T11:32:50.827457Z 0 [Note] /home/data_sas/ltdb/bin/mysqld: Shutdown complete

-------- Storage Engine Statistics -----------------------------------------------------------------
[--] Status: +ARCHIVE +BLACKHOLE +CSV -FEDERATED +InnoDB +MEMORY +MRG_MYISAM +MyISAM +PERFORMANCE_SCHEMA
[--] Data in InnoDB tables: 1.1G (Tables: 14)
[OK] Total fragmented tables: 0

-------- Analysis Performance Metrics --------------------------------------------------------------
[--] innodb_stats_on_metadata: OFF
[OK] No stat updates during querying INFORMATION_SCHEMA.

-------- Security Recommendations ------------------------------------------------------------------
[OK] There are no anonymous accounts for any database users
[OK] All database users have passwords assigned
[!!] User 'lt@%' has user name as password.
[!!] User 'lttest@%' has user name as password.
[!!] User 'lt@%' does not specify hostname restrictions.
[!!] User 'lttest@%' does not specify hostname restrictions.
[!!] User 'repl@%' does not specify hostname restrictions.
[!!] User 'root@%' does not specify hostname restrictions.
[!!] There is no basic password file list!

-------- CVE Security Recommendations --------------------------------------------------------------
[--] Skipped due to --cvefile option undefined

-------- Performance Metrics -----------------------------------------------------------------------
[--] Up for: 14d 3h 25m 44s (608K q [0.498 qps], 693 conn, TX: 27M, RX: 3G)
[--] Reads / Writes: 49% / 51%
[--] Binary logging is enabled (GTID MODE: ON)
[--] Physical Memory     : 251.9G
[--] Max MySQL memory    : 191.1G
[--] Other process memory: 0B
[--] Total buffers: 680.0M global + 19.5M per thread (10000 max threads)
[--] P_S Max memory usage: 72B
[--] Galera GCache Max memory usage: 0B
[OK] Maximum reached memory usage: 738.5M (0.29% of installed RAM)
[OK] Maximum possible memory usage: 191.1G (75.87% of installed RAM)
[OK] Overall possible memory usage with other process is compatible with memory available
[OK] Slow queries: 0% (0/608K)
[OK] Highest usage of available connections: 0% (3/10000)
[OK] Aborted connections: 2.60%  (18/693)
[OK] Query cache is disabled by default due to mutex contention on multiprocessor machines.
[OK] Sorts requiring temporary tables: 0% (0 temp sorts / 20 sorts)
[OK] No joins without indexes
[OK] Temporary tables created on disk: 7% (425 on disk / 5K total)
[--] Thread cache not used with thread_handling=pool-of-threads
[OK] Table cache hit rate: 58% (1K open / 3K opened)
[OK] table_definition_cache(1424) is upper than number of tables(293)
[OK] Open file limit used: 0% (62/65K)
[OK] Table locks acquired immediately: 100% (308 immediate / 308 locks)
[OK] Binlog cache memory access: 96.33% (196602 Memory / 204100 Total)

-------- Performance schema ------------------------------------------------------------------------
[--] Memory used by P_S: 72B
[--] Sys schema is installed.

-------- ThreadPool Metrics ------------------------------------------------------------------------
[--] ThreadPool stat is enabled.
[--] Thread Pool Size: 48 thread(s).
[!!] thread_pool_size between 16 and 36 when using InnoDB storage engine.

-------- MyISAM Metrics ----------------------------------------------------------------------------
[!!] Key buffer used: 18.3% (3M used / 16M cache)
[OK] Key buffer size / total MyISAM indexes: 16.0M/43.0K
[OK] Read Key buffer hit rate: 98.0% (356 cached / 7 reads)
[OK] Write Key buffer hit rate: 100.0% (4 cached / 4 writes)

-------- InnoDB Metrics ----------------------------------------------------------------------------
[--] InnoDB is enabled.
[--] InnoDB Thread Concurrency: 0
[OK] InnoDB File per table is activated
[!!] InnoDB buffer pool / data size: 640.0M/1.1G
[!!] Ratio InnoDB log file size / InnoDB Buffer pool size (640 %): 2.0G * 2/640.0M should be equal to 25%
[OK] InnoDB buffer pool instances: 1
[--] Number of InnoDB Buffer Pool Chunk : 5 for 1 Buffer Pool Instance(s)
[OK] Innodb_buffer_pool_size aligned with Innodb_buffer_pool_chunk_size & Innodb_buffer_pool_instances
[OK] InnoDB Read buffer efficiency: 100.00% (125308826 hits/ 125309343 total)
[OK] InnoDB Write log efficiency: 98.33% (12229501 hits/ 12437186 total)
[OK] InnoDB log waits: 0.00% (0 waits / 207685 writes)

-------- AriaDB Metrics ----------------------------------------------------------------------------
[--] AriaDB is disabled.

-------- TokuDB Metrics ----------------------------------------------------------------------------
[--] TokuDB is disabled.

-------- XtraDB Metrics ----------------------------------------------------------------------------
[--] XtraDB is disabled.

-------- Galera Metrics ----------------------------------------------------------------------------
[--] Galera is disabled.

-------- Replication Metrics -----------------------------------------------------------------------
[--] Galera Synchronous replication: NO
[--] No replication slave(s) for this server.
[--] Binlog format: ROW
[--] XA support enabled: ON
[--] Semi synchronous replication Master: ON
[--] Semi synchronous replication Slave: OFF
[--] This is a standalone server

-- 关注建议部分
-------- Recommendations ---------------------------------------------------------------------------
General recommendations:
    Control warning line(s) into /home/data_sas/ltdb1/log/mysqld1.log file
    Control error line(s) into /home/data_sas/ltdb1/log/mysqld1.log file
    Set up a Secure Password for lt@% user: SET PASSWORD FOR 'lt'@'SpecificDNSorIp' = PASSWORD('secure_password');
    Set up a Secure Password for lttest@% user: SET PASSWORD FOR 'lttest'@'SpecificDNSorIp' = PASSWORD('secure_password');
    Restrict Host for 'lt'@% to lt@SpecificDNSorIp
    UPDATE mysql.user SET host ='SpecificDNSorIp' WHERE user='lt' AND host ='%'; FLUSH PRIVILEGES;
    Restrict Host for 'lttest'@% to lttest@SpecificDNSorIp
    UPDATE mysql.user SET host ='SpecificDNSorIp' WHERE user='lttest' AND host ='%'; FLUSH PRIVILEGES;
    Restrict Host for 'repl'@% to repl@SpecificDNSorIp
    UPDATE mysql.user SET host ='SpecificDNSorIp' WHERE user='repl' AND host ='%'; FLUSH PRIVILEGES;
    Restrict Host for 'root'@% to root@SpecificDNSorIp
    UPDATE mysql.user SET host ='SpecificDNSorIp' WHERE user='root' AND host ='%'; FLUSH PRIVILEGES;
    Thread pool size for InnoDB usage (48)
    Before changing innodb_log_file_size and/or innodb_log_files_in_group read this: https://bit.ly/2TcGgtU
Variables to adjust:
    thread_pool_size between 16 and 36 for InnoDB usage
    innodb_buffer_pool_size (>= 1.1G) if possible.
    innodb_log_file_size should be (=80M) if possible, so InnoDB total log files size equals to 25% of buffer pool size.
```

