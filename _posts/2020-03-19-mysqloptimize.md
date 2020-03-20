---
layout:     post                    
title:     	mysql的磁盘碎片清理功能										
subtitle:   mysql的磁盘碎片清理以及进度查看
date:       2020-03-19            
author:     Qiyibaba               
header-img: img/post-bg-202003.jpg   
catalog: true                     
tags:                               
    - online ddl
    - mysql
---

### mysql的磁盘碎片清理功能

| command | optimize                                                     | alter table ENGINE = InnoDB , ALGORITHM=INPLACE, LOCK=NONE   |
| ------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
|         | [官方文档](https://dev.mysql.com/doc/refman/5.7/en/optimize-table.html) | [官方文档](https://dev.mysql.com/doc/refman/5.7/en/alter-table.htmlhttps://dev.mysql.com/doc/refman/5.7/en/innodb-file-defragmenting.html) |
| Engine  | InnoDB、 `MyISAM` or `ARCHIVE`<br>does not work with views.<br>is supported for partitioned tables | InnoDB                                                       |
| InnoDB  | 要求有 [.ibd file](https://dev.mysql.com/doc/refman/5.7/en/glossary.html#glos_ibd_file)文件 |                                                              |
|         | `要求``innodb_file_per_table` =1                             | 要求innodb-file-per-table=1 并且select @@innodb_file_per_table;  结果也为1 |
|         | requires [`SELECT`](https://dev.mysql.com/doc/refman/5.7/en/privileges-provided.html#priv_select) and [`INSERT`](https://dev.mysql.com/doc/refman/5.7/en/privileges-provided.html#priv_insert) privileges | use [online DDL](https://dev.mysql.com/doc/refman/5.7/en/innodb-online-ddl.html)，concurrent DML is supported |
|         | 有全文索引的表， [`innodb_optimize_fulltext_only=1`](https://dev.mysql.com/doc/refman/5.7/en/innodb-parameters.html#sysvar_innodb_optimize_fulltext_only) | 有全文索引的表，会对表上X锁，一直到整理全表结束              |

- optimize和alter table都只有fulltext的限制条件，经过实验，innodb_file_per_table  = 0也是支持online DDL的。

- 碎片整理命令，无论是optimize还是alter table ENGINE = InnoDB , ALGORITHM=INPLACE, LOCK=NONE均会同步到备机,只有 optimize NO_WRITE_TO_BINLOG table xxx 才不会同步


- 备机binlog示例：

  ```
  #191230 15:15:33 server id 16782863  end_log_pos 1901 CRC32 0x8a3d0255  GTID    last_committed=8        sequence_number=9       rbr_only=no SET @@SESSION.GTID_NEXT= 'e108f58d-0cd8-11ea-bd66-287b09c271a4:269469'/*!*/; # at 1901 #191230 15:15:33 server id 16782863  end_log_pos 1997 CRC32 0x4718a59d  Query   thread_id=2     exec_time=26    error_code=0 use `test`/*!*/; SET TIMESTAMP=1577690133/*!*/; optimize table t2 /*!*/; # at 1997 #191230 15:18:39 server id 16782863  end_log_pos 2062 CRC32 0x65a9a789  GTID    last_committed=9        sequence_number=10      rbr_only=no SET @@SESSION.GTID_NEXT= 'e108f58d-0cd8-11ea-bd66-287b09c271a4:269470'/*!*/; # at 2062 #191230 15:18:39 server id 16782863  end_log_pos 2201 CRC32 0x028fc877  Query   thread_id=2     exec_time=22    error_code=0 SET TIMESTAMP=1577690319/*!*/; alter table t2 engine=innodb  , ALGORITHM=INPLACE, LOCK=NONE /*!*/; SET @@SESSION.GTID_NEXT= 'AUTOMATIC' /* added by mysqlbinlog */ /*!*/; DELIMITER ; # End of log file /*!50003 SET COMPLETION_TYPE=@OLD_COMPLETION_TYPE*/; /*!50530 SET @@SESSION.PSEUDO_SLAVE_MODE=0*/;
  ```
  
- 对于innodb引擎来说optimize 是ALTER TABLE ... FORCE的别名

- 有全文索引，alter table t2 engine=innodb  , ALGORITHM=INPLACE, LOCK=NONE会报错，但optimize table t2不会

  ```mysql
  mysql> alter table t2 engine=innodb  , ALGORITHM=INPLACE, LOCK=NONE;   
  ERROR 1846 (0A000): ALGORITHM=INPLACE is not supported. Reason: InnoDB presently supports one FULLTEXT index creation at a time. Try ALGORITHM=COPY.
  -- 只允许  alter table t2 engine=innodb,ALGORITHM=COPY;这种方式执行
  -- optimize 在这中情况下，仅仅会阻塞DML操作，不会报错
  ```

- 推荐使用alter table t2 engine=innodb  , ALGORITHM=INPLACE, LOCK=NONE方式，因为DB会自己报错 

> 执行阶段加的锁是SHARED_UPGRADABLE，该阶段允许并行读写；Prepare和Commit阶段，加的锁是EXCLUSIVE，这两个阶段不能并行DML，Prepare和Commit阶段的耗时很短，占整个DDL流程比例非常小，对业务影响可以忽略不计。MDL_SHARED_UPGRADABLE之间是互斥的，所以可以保证同一张表不会并行执行多个DDL。

 

### 监控Online DDL进度

Mysql5.7提供了监控DDL进度的功能，可以通过该功能实时查看DDL执行进展。

#### 开启统计DDL进度功能

```sql
UPDATE performance_schema.setup_instruments SET ENABLED = 'YES', TIMED = 'YES' WHERE NAME = 'stage/sql/altering table';
UPDATE performance_schema.setup_instruments SET ENABLED = 'YES', TIMED = 'YES' WHERE NAME = 'stage/%';
UPDATE performance_schema.setup_consumers SET ENABLED = 'YES' WHERE NAME LIKE 'events_stages%';
UPDATE performance_schema.setup_timers SET TIMER_NAME = 'MICROSECOND' WHERE NAME = 'stage';
UPDATE performance_schema.setup_instruments SET ENABLED='YES' WHERE NAME='stage/sql/copy to tmp table';
UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME LIKE 'events_stages_%';
```

#### 查看DDL进度

```mysql
SELECT EVENT_NAME, WORK_COMPLETED, WORK_ESTIMATED,(WORK_COMPLETED/WORK_ESTIMATED)*100 as COMPLETED FROM performance_schema.events_stages_current;
```

进度信息说明：

| 字段名         | 字段说明                                                     |
| -------------- | ------------------------------------------------------------ |
| EVENT_NAME     | 记录DDL当前执行阶段的名称，Online DDL包括以下阶段：read PK and internal sortmerge sortinsertflushlog apply table |
| WORK_ESTIMATED | 该阶段预期工作单元总数                                       |
| WORK_COMPLETED | 该阶段已完成工作单元数                                       |
| COMPLETED      | 通过WORK_COMPLETED/WORK_ESTIMATED计算完成百分比              |

#### 案例

```
mysql> alter table sbtest1 ENGINE = InnoDB , ALGORITHM=INPLACE, LOCK=NONE;
Query OK, 0 rows affected (1 min 5.58 sec)
Records: 0  Duplicates: 0  Warnings: 0
```

##### 过程检测脚本

```shell
event="begin"
etime=0
otime=0
c="mysql -uroot -pdb10\$ZTE"
while true
do
        line=`$c -e "SELECT concat(EVENT_NAME,'|',(WORK_COMPLETED/WORK_ESTIMATED)*100) FROM performance_schema.events_stages_current;" 2>/dev/null | sed '1d'`
        tevent=`echo $line | awk -F'|' '{print $1}'`
        tproc=`echo $line | awk -F'|' '{print $2}'`

        #if [ "x$tevent" == "x$event" ];then
                #ecost=`echo "$ecost+0.01"|bc`
        #else
        if [ "x$tevent" != "x$event" ];then
                if [ "x$event" == "xbegin" -a "x$tevent" == "x" ];then
                        continue
                elif [ "x$event" != "xbegin" -a "x$tevent" == "x" ];then
                        echo "$event total cost $ecost s"
                        exit
                else
                        #echo "$event total cost $ecost s"
                        etime=`date +%s`
                        if [ "x$event" != "xbegin" ];then
                                echo "$event total cost `echo "$etime-$otime"|bc` s"
                        fi
                        otime=$etime
                        event=$tevent
                        ecost=0
                fi
        fi
        sleep 0.1
done
```

##### 结果输出

```
[ltdb1@db02 ~]$ ./moniddl.sh
stage/innodb/alter table (read PK and internal sort) total cost 43 s
stage/innodb/alter table (merge sort) total cost 3 s
stage/innodb/alter table (insert) total cost 2 s
stage/innodb/alter table (flush) total cost 30 s
stage/innodb/alter table (log apply table) total cost 0 s
```

### 附：碎片率超过50%的表检查

```mysql
SELECT
	TABLE_SCHEMA AS `db`,
	TABLE_NAME AS `tbl`,
	1- ( TABLE_ROWS * AVG_ROW_LENGTH ) / ( DATA_LENGTH + INDEX_LENGTH + DATA_FREE ) AS `fragment_pct` 
FROM
	information_schema.TABLES 
WHERE
	TABLE_SCHEMA NOT IN ( 'information_schema', 'mysql', 'performance_schema', 'sys' ) 
	AND ( 1- ( TABLE_ROWS * AVG_ROW_LENGTH ) / ( DATA_LENGTH + INDEX_LENGTH + DATA_FREE ) ) > 0.5 
	AND ( DATA_LENGTH + INDEX_LENGTH + DATA_FREE ) > 1024 * 1024 * 1024;
```

### 附：碎片清理需要磁盘空间
由于碎片整理采用的是copy table的方式，所以会另外占用磁盘空间，需要在执行之前对主备机进行磁盘空间检测，确保磁盘空间足够，需要磁盘空间分2块：
1.与原表ibd文件大小一样的空间
2.innodb_online_alter_log_max_size配置大小的空间，默认128M，用来存放执行过程中的DML，如果业务并发很大，需修改该配置的大小，支持动态修改。
