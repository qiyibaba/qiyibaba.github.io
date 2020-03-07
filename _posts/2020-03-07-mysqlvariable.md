---
layout:     post                    
title:      MySQL推荐修改配置参考
subtitle:   下文展示可能需要修改的MySQL配置项，按照实际情况修改
date:       2020-03-06          
author:     Qiyibaba               
header-img: img/post-bg-202003.jpg   
catalog: true                     
tags:                               
    - mysql

---

| 参数名                            | 参数值              | 描述                                                         |
| --------------------------------- | ------------------- | ------------------------------------------------------------ |
| lower_case_table_names            | 1                   | 0: 库名和表名区分大小写;0:区分大小写，DDL中所有库名和表名使用原名 1:不区分大小写 DDL所有库名和表名都使用小写 |
| general_query                     | 0                   | 0: 不开启; 1: 开启                                           |
| slow_query_log                    | 0                   | 0: 不开启; 1: 开启                                           |
| long_query_time                   | 10000               | 慢查询日志时间阈值，单位1ms                                  |
| sql_mode                          | STRICT_TRANS_TABLES | 合理取值：ALLOW_INVALID_DATES，ANSI_QUOTES，ERROR_FOR_DIVISION_BY_ZERO，HIGH_NOT_PRECEDENCE，IGNORE_SPACE，NO_AUTO_CREATE_USER，NO_AUTO_VALUE_ON_ZERO，NO_BACKSLASH_ESCAPES，NO_DIR_IN_CREATE，NO_ENGINE_SUBSTITUTION，NO_FIELD_OPTIONS，NO_KEY_OPTIONS，NO_TABLE_OPTIONS，NO_UNSIGNED_SUBTRACTION，NO_ZERO_DATE，NO_ZERO_IN_DATE，ONLY_FULL_GROUP_BY，PAD_CHAR_TO_FULL_LENGTH，PIPES_AS_CONCAT，REAL_AS_FLOAT，STRICT_ALL_TABLES，STRICT_TRANS_TABLES |
| expire_logs_days                  | 3                   | 0-不清除日志，单位:天                                        |
| performance_schema                | ON                  | 性能监控开关：ON 或 OFF                                      |
| innodb_buffer_pool_size           | 629145600           | buffer pool大小：5242880~2**64-1，单位byte                   |
| innodb_buffer_pool_instances      | 8                   | buffer pool个数                                              |
| innodb_flush_log_at_trx_commit    | 1                   | 0-每秒写入日志并刷到磁盘，1-每次事务提交时写入日志并刷到磁盘，2-每次事务提交时只写入日志文件 |
| innodb_io_capacity                | 2000                | 控制Innodb checkpoint时IO能力                                |
| innodb_log_files_in_group         | 2                   | redo log日志文件数                                           |
| innodb_log_file_size              | 2048M               | 每个redo log文件大小                                         |
| innodb_read_io_threads            | 16                  | innodb读IO线程数                                             |
| innodb_write_io_threads           | 16                  | innodb写IO线程数                                             |
| innodb_flush_neighbors            | 1                   | 0-不刷新邻接页，1-刷新邻接页，并刷新buffer pool中位于磁盘上相同的extend区的相邻脏页，2-刷新buffer pool中位于磁盘上相同的extend区的脏页。如果是固态硬盘，建议配置成0。 |
| innodb_open_files                 | 65535               | innodb打开文件的最大个数                                     |
| innodb_flush_method               | O_DIRECT            | 控制innodb数据文件及redo log的打开、刷写模式,影响io吞吐量。fdatasync模式：调用fsync()去刷数据文件与redo log的buffer。O_DSYNC模式：innodb会使用O_SYNC方式打开和刷写redo log, 使用fsync()刷写数据文件。 O_DIRECT模式：innodb使用O_DIRECT打开数据文件，使用fsync()刷写数据文件和redo log |
| auto_increment_increment          | 8                   | 自增字段步长                                                 |
| auto_increment_offset             | 1                   | 自增字段起始值                                               |
| tmp_table_max_rows                | 0                   | 设置临时表最大行数                                           |
| transaction_max_binlog_size       | 0                   | 设置事务最大binlog大小                                       |
| thread_pool_oversubscribe         | 16                  | 设置同一时间活跃线程数                                       |
| group_concat_max_len              | 1024                | 规定了组合而成的字符串的最大长度（超长则截断）               |
| innodb_lock_wait_max_depth        | 0                   | 规定了锁的最大深度                                           |
