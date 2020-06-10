---
layout:     post                    
title:     	软件开发基础知识										
subtitle:   软件开发基础知识：数据库篇，shell篇，hadoop篇
date:       2020-06-10            
author:     Qiyibaba               
header-img: img/post-bg-202003.jpg   
catalog: true                     
tags:                               
    - sql
    - mysql
    - shell
    - hadoop
---

### 数据库篇

关于数据库部分，我们将从案例着手来了解数据库的常用SQL，在此之前，我们先来了解一些基本概念。

```
*数据库的分类：
	1.关系型数据库，特点范式设计，代表MySQL、Oracle、Microsoft SQL Server、Access及PostgreSQL等
	2.非关系型数据库，特点反范式设计，代表BigTable（Google）、Cassandra、MongoDB、CouchDB等；
*本文主要讨论的就是关系型数据库中的mysql，mysql是甲骨文（Oracle）开源的基于插件的关系型数据库，由于代码的开源，其还存在一些兄弟，如mariadb和percona。
*数据库的构成：行，列（逻辑），页（物理），表（逻辑），表空间（物理），想象成一个房间。
*数据库需要满足：ACID【A原子性、C一致性、I独立性、D持久性】--涉及到了事务的概念，事务要做的是什么，就是我在买奶茶，后面的需要排队，否则就会出现，他抢了我的奶茶，确实我付的钱的情况。
*SQL（Structured Query Language 结构化查询语句）是一种特定目的程序语言，用于管理关系数据库管理系统（RDBMS），或在关系流数据管理系统（RDSMS）中进行流处理。
*在某些定义上，SQL是一个广泛的概念，包括（ddl"data design language",dml"data merge language",dql"data query language"），其中ddl大概有“create drop”两个关键字，dml有“RUID”，replace替换，update更新，insert插入，delete删除四类，dql主要就是查询，当前还有权限管理等，不在当前讨论的范围内。目前的SQL主要是基于SQL 92规范上做了一些扩充。
```

正文开始：我们要建立一个学校的数据库，需要如下几张表：

```
学生表 student(s_id,s_name,s_birth,s_sex) -- 学生编号,学生姓名, 出生年月,学生性别 
课程表 course(c_id,c_name,t_id)  -- 课程编号, 课程名称, 教师编号 
教师表 teacher(t_id,t_name) -– 教师编号,教师姓名 
成绩表 score(s_id,c_id,s_score)  --学生编号,课程编号,分数
```

DDL首先要做的就是建表操作：

```mysql
DROP TABLE IF EXISTS `course`;
CREATE TABLE `course`  (
  `c_id` varchar(20) NOT NULL DEFAULT '' COMMENT '课程编号',
  `c_name` varchar(20) NOT NULL DEFAULT '' COMMENT '课程名称',
  `t_id` varchar(20) NOT NULL COMMENT '教师编号',
  PRIMARY KEY (`c_id`));
DROP TABLE IF EXISTS `score`;
CREATE TABLE `score`  (
  `s_id` varchar(20) NOT NULL DEFAULT '' COMMENT '学生编号',
  `c_id` varchar(20) NOT NULL DEFAULT '' COMMENT '课程编号',
  `s_score` int(3) NULL DEFAULT NULL COMMENT '分数',
  PRIMARY KEY (`s_id`, `c_id`) USING BTREE);
DROP TABLE IF EXISTS `student`;
CREATE TABLE `student`  (
  `s_id` varchar(20) NOT NULL DEFAULT '' COMMENT '学生编号',
  `s_name` varchar(20) NOT NULL DEFAULT '' COMMENT '学生姓名',
  `s_birth` varchar(20) NOT NULL DEFAULT '' COMMENT '出生年月',
  `s_sex` varchar(10) NOT NULL DEFAULT '' COMMENT '学生性别',
  PRIMARY KEY (`s_id`));
DROP TABLE IF EXISTS `teacher`;
CREATE TABLE `teacher`  (
  `t_id` varchar(20) NOT NULL DEFAULT '' COMMENT '教师编号',
  `t_name` varchar(20)NOT NULL DEFAULT '' COMMENT '教师姓名',
  PRIMARY KEY (`t_id`));
```

1. 上例中使用了drop 和create两类，drop就是删除表，create就是新增表，为了能够重复执行sql，所以加上了drop table，又为了保证在没有表存在的情况删除表不会报错，所以又加上了if exists
2. 表的列的定义由列名，列类型，列属性构成，列名表内唯一就可以了，列类型记住几个就行了，int用来存整数，decimal或者number用来存钱，带小数的数值，varchar用来存字符串，要跟上长度，如varchar(20),timestamp存时间，其他类型用的不多。属性基本就三个，是否可以为空，不可以为空就加上not null，默认值default，注释COMMENT
3. 表会有主键和索引，主键PRIMARY KEY，索引index，主键是特殊的索引（not null的唯一索引），索引是什么，就是书的目录，通过查询目录可以直接知道数据在书的第几页，主键就相当于页码，不会存在2个相同的页码，目录对应的就是页码，用数据库的解释就是，索引存的数据就是主键的值。

DML：数据插入（insert）

```mysql
INSERT INTO `course` VALUES ('01', '语文', '02'),('02', '数学', '01'),('03', '英语', '03');
INSERT INTO `score` VALUES ('01', '01', 80),('01', '02', 90),('01', '03', 99),('02', '01', 70),('02', '02', 60),('02', '03', 80),('03', '01', 80),('03', '02', 80),('03', '03', 80),('04', '01', 50),('04', '02', 30),('04', '03', 20),('05', '01', 76),('05', '02', 87),('05', '03', 95),('06', '01', 31),('06', '02', 88),('06', '03', 34),('07', '01', 66),('07', '02', 89),('07', '03', 98),('08', '01', 59),('08', '02', 88),('09', '02', 67),('09', '03', 88),('10', '01', 65),('10', '02', 78);
INSERT INTO `student` VALUES ('01', '斯内克', '1990-01-01', '男'),('02', '张益达', '1990-12-21', '男'),('03', '张大炮', '1990-05-20', '男'),('04', '李云龙', '1990-08-06', '男'),('05', '楚云飞', '1991-12-01', '女'),('06', '赵日天', '1992-03-01', '女'),('07', '小甜甜', '1989-07-01', '女'),('08', '王菊花', '1990-01-20', '女'),('09', '李慕白', '1994-01-20', '男'),('10', '东京热', '1980-01-20', '女');
INSERT INTO `teacher` VALUES ('01', '墨白');
INSERT INTO `teacher` VALUES ('02', '默狐');
INSERT INTO `teacher` VALUES ('03', '柠檬');
```

1. 上述插入涉及到了2.类语法，insert单行和insert多行

2. replace是替换插入，即数据不存在的时候等同于insert，数据存在的时候相当于update

3. update语法示例'墨白老师改名叫墨黑'：update teacher set t_name='墨黑' where t_name='墨白'

4. delete语法示例’李云龙退学了‘：delete from student where s_name=’李云龙‘

5. 特殊用法（扩展学习）：

   ```
   1.insert... select ...我们希望将teacher表扩展一份，防止主表丢失,
   提前有表：insert into teacher_bak select * from teacher
   提前无表：create table teacher_bak select * from teacher --顺带建表
   2.truncate 是一种特殊的ddl，功能相当于delete from xxx，清理表全部数据，但是更快哦
   ```

DQL：数据查询（重头戏来了）：

```
			-单表查询
query -多表查询  -连接（重要）
							  -嵌套（子查询，重要）
							  -派生表
							  -集合查询			
```

连接总结：

![image-20200610104402103](/Users/qiyibaba/Library/Application Support/typora-user-images/image-20200610104402103.png)

为什么会有嵌套的存在，数据表的查询结果，在逻辑上其实还是一个表结构（规范结构），所以会存在嵌套子表，即把子表当成了一个独立的表，当然子表肯定也是经过过滤数据之后的，否则就无意义了。

来几个案例一起看看吧,主要能认清语法格式：

1、查询共有多少同学，关键字count(*)

```
select count(*) from student;
```

2、查看班级的男生姓名，关键字where

```
select s_name from student where s_sex=’男‘
```

3、统计每年出生的同学个数，关键字group by,order by

```
select substr(s_birth,1,4),count(*) from student group by substr(s_birth,1,4) order by substr(s_birth,1,4);
```

4、查看共有几种性别,关键字distinct

```
select distinct s_sex from student;
```

5、查询所有人的语文成绩，关键字子查询in，子查询其实可以单独执行

```
select * from score where c_id in (select c_id from course where c_name = '语文');
```

6、查询"语文"课程比"数学"课程成绩高的学生的信息及课程分数，关键字left join

```
SELECT
   st.*,
   sc.s_score AS '语文',
   sc2.s_score '数学' 
FROM
   student st
   LEFT JOIN score sc ON sc.s_id = st.s_id 
   AND sc.c_id = '01'
   LEFT JOIN score sc2 ON sc2.s_id = st.s_id 
   AND sc2.c_id = '02' 
WHERE
    sc.s_score > sc2.s_score
```

7、查询1990年出生的学生名单，关键字like

```
SELECT st.* FROM student st WHERE st.s_birth LIKE "1990%";
```

### Shell命令篇

#### 目录操作

##### 基本操作

| cmd        | comment                         |
| ---------- | ------------------------------- |
| mkdir      | 创建目录  ,eg：mkdir -p a/b/c/d |
| cp(copy)   | 拷贝文件 ,eg: cp -rvf a/ /tmp/  |
| mv(move)   | 移动文件  :eg: mv -vf a /tmp/b  |
| rm(remove) | 删除文件 :eg: rm -rf /          |

##### 漫游

linux上是黑漆漆的命令行，依然要面临人生三问：我是谁？我在哪？我要去何方？

| cmd  | comment                                                      |
| ---- | ------------------------------------------------------------ |
| ls   | 命令能够看到当前目录的所有内容。ls -l能够看到更多信息，判断你是谁。 |
| pwd  | 命令能够看到当前终端所在的目录。告诉你你在哪。               |
| cd   | 假如你去错了地方，cd命令能够切换到对的目录。                 |
| find | find命令通过筛选一些条件，能够找到已经被遗忘的文件。         |

至于要去何方，可能就是主宰者的意志了。

#### 文本处理

这是是非常非常加分的技能。get到之后，也能节省更多时间来研究面向对象。![img](file:////Users/qiyibaba/Library/Group%20Containers/UBF8T346G9.Office/TemporaryItems/msohtmlclip/clip_image002.jpg)

##### 查看

| cmd  | comment                                                      |
| ---- | ------------------------------------------------------------ |
| cat  | 查看文件，注意，如果文件很大的话，cat命令的输出结果会疯狂在终端上输出，可以多次按ctrl+c终止。 |
| du   | 查看文件大小:du -h file                                      |
| less | 类似vim，less可以在输入/后进入查找模式，然后按n(N)向下(上)查找。   有许多操作，都和vim类似，你可以类比看下。 |
| tail | tail -f access.log  tail命令可以静态的查看某个文件的最后n行，与之对应的， |
| head | 命令查看文件头n行。但head没有滚动功能，就像尾巴是往外长的，不会反着往里长。 |

##### 统计

sort和uniq经常配对使用。sort可以使用-t指定分隔符，使用-k指定要排序的列。

```shell
#下面这个命令输出nginx日志的ip和每个ip的pv，pv最高的前10
\#2019-06-26T10:01:57+08:00|nginx001.server.ops.pro.dc|100.116.222.80|10.31.150.232:41021|0.014|0.011|0.000|200|200|273|-|/visit|sign=91CD1988CE8B313B8A0454A4BBE930DF|-|-|http|POST|112.4.238.213
 awk -F"|" '{print $3}' access.log | sort | uniq -c | sort -nk1 -r | head -n10
```

##### 其他

| cmd  | comment                                                      |
| ---- | ------------------------------------------------------------ |
| grep | grep用来对内容进行过滤，带上--color参数，可以在支持的终端可以打印彩色，参数n则输出具体的行数，用来快速定位。  比如：查看nginx日志中的POST请求。<br>grep -rn --color POST access.log.    grep -rn --color Exception -A10 -B2  error.log<br>A after  内容后n行<br/>B before  内容前n行<br/>C count?  内容前后n行 |
| diff | diff命令用来比较两个文件是否的差异。当然，在ide中都提供了这个功能，diff只是命令行下的原始折衷。对了，diff和patch还是一些平台源码的打补丁方式，你要是不用，就pass吧。 |

#### 压缩

为了减小传输文件的大小，一般都开启压缩。linux下常见的压缩文件有tar、bzip2、zip、rar等，7z这种用的相对较少。
 ![img](file:////Users/qiyibaba/Library/Group%20Containers/UBF8T346G9.Office/TemporaryItems/msohtmlclip/clip_image003.jpg)
 .tar 使用tar命令压缩或解压; .bz2 使用bzip2命令操作; .gz 使用gzip命令操作;.zip 使用unzip命令解压; .rar 使用unrar命令解压.最常用的就是.tar.gz文件格式了。其实是经过了tar打包后，再使用gzip压缩。

```shell
#创建压缩文件
tar -cvfz archive.tar.gz dir/
#解压
tar -xvfz archive.tar.gz
```

#### 日常运维

开机是按一下启动按钮，关机总不至于是长按启动按钮吧。对了，是shutdown命令，不过一般也没权限-.-!。passwd命令可以用来修改密码，这个权限还是可以有的。

| cmd       | Comment                                                      |
| --------- | ------------------------------------------------------------ |
| mount     | mount命令可以挂在一些外接设备，比如u盘，比如iso，比如刚申请的ssd。可以放心的看小电影了。mount /dev/sdb1 /xiaodianying |
| chown     | 用来改变文件的所属用户和所属组chown -R xjj:xjj a             |
| chmod     | 用来改变文件的访问权限 chmod a+x a.sh                        |
| yum       | 假定你用的是centos，则包管理工具就是yum。如果你的系统没有wget命令，就可以使用如下命令进行安装。yum install wget -y |
| systemctl | 系统参数控制                                                 |
| kill      | 对于普通的进程，就要使用kill命令进行更加详细的控制了。kill命令有很多信号，如果你在用kill -9，你一定想要了解kill -15以及kill -3的区别和用途。 |
| su        | su用来切换用户。比如你现在是root，想要用xjj用户做一些勾当，就可以使用su切换 |
| shutdown  | 关机                                                         |
| password  | 修改密码                                                     |

#### 系统状态概览

登陆一台linux机器，有些命令能够帮助你快速找到问题。这些命令涵盖内存、cpu、网络、io、磁盘等。

| cmd      | comment                                                      |
| -------- | ------------------------------------------------------------ |
| uname    | uname命令可以输出当前的内核信息，让你了解到用的是什么机器。uname -a |
| ps       | ps命令能够看到进程/线程状态。和top有些内容重叠，常用。ps -ef |
| top      | 系统状态一览，主要查看。cpu load负载、cpu占用率。使用内存或者cpu最高的一些进程。下面这个命令可以查看某个进程中的线程状态。top -H -p pid |
| free     | top也能看内存，但不友好，free是专门用来查看内存的。包括物理内存和虚拟内存swap。 |
| df       | df命令用来查看系统中磁盘的使用量，用来查看磁盘是否已经到达上限。参数h可以以友好的方式进行展示。 |
| ifconfig | 查看ip地址，不啰嗦，替代品是ip addr命令。                    |
| ping     | 至于网络通不通，可以使用ping来探测。（不包括那些禁ping的网站） |
| netstat  | 虽然ss命令可以替代netstat了，但现实中netstat仍然用的更广泛一些。比如，查看当前的所有tcp连接。netstat -ant，替代lsof |

#### 工作常用

还有一些在工作中经常会用到的命令，它们的出现频率是非常高的 ，都是些熟面孔。

| cmd     | comment                                                      |
| ------- | ------------------------------------------------------------ |
| export  | 很多安装了jdk的同学找不到java命令，export就可以帮你办到它。export用来设定一些环境变量，env命令能看到当前系统中所有的环境变量。比如，下面设置的就是jdk的。 |
| which   | 位置寻找                                                     |
| crontab | 这就是linux本地的job工具。不是分布式的，你要不是运维，就不要用了。比如，每10分钟提醒喝茶上厕所。*/10 * * * * /home/xjj/wc10min |
| date    | date命令用来输出当前的系统时间，可以使用-s参数指定输出格式。但设置时间涉及到设置硬件，所以有另外一个命令叫做hwclock。 |
| xargs   | xargs读取输入源，然后逐行处理。这个命令非常有用。举个栗子，删除目录中的所有class文件。 |

```shell
find . | grep .class$ | xargs rm -rvf
#把所有的rmvb文件拷贝到目录
ls *.rmvb | xargs -n1 -i cp {} /mount/xiaodianying
```

#### 网络

linux是一个多作业的网络操作系统，所以网络命令有很多很多。工作中，最常和这些打交道。

| cmd  | comment                                                      |
| ---- | ------------------------------------------------------------ |
| ssh  | 远程访问                                                     |
| scp  | scp用来进行文件传输。也可以用来传输目录。也有更高级的sftp命令。 |
| wget |                                                              |

###  Hadoop篇

​	百度百科：Hadoop是一个由Apache基金会所开发的[分布式系统](https://baike.baidu.com/item/分布式系统/4905336)基础架构。用户可以在不了解分布式底层细节的情况下，开发分布式程序。充分利用集群的威力进行高速运算和存储。Hadoop实现了一个[分布式文件系统](https://baike.baidu.com/item/分布式文件系统/1250388)（Hadoop Distributed File System），简称HDFS。HDFS有高[容错性](https://baike.baidu.com/item/容错性/9131391)的特点，并且设计用来部署在低廉的（low-cost）硬件上；而且它提供高吞吐量（high throughput）来访问[应用程序](https://baike.baidu.com/item/应用程序/5985445)的数据，适合那些有着超大数据集（large data set）的应用程序。HDFS放宽了（relax）POSIX的要求，可以以流的形式访问（streaming access）文件系统中的数据。Hadoop的框架最核心的设计就是：HDFS和MapReduce。HDFS为海量的数据提供了存储，而MapReduce则为海量的数据提供了计算。

知道几个点就行了：

1. 分布式存储系统，扩展了单机数据库的存储能力
2. 存储的文件格式是hdfs
3. 理论上都是分布式的系统，类似于ob和goldendb一样，都可以做分布式计算，提升吞吐量
4. 可以做基于posix（一种多节点的投票机制）的集群部署
5. 主要用来做日志分析的
6. 未来es（Elasticsearch）才是首选，已经有ES-Hadoop，集成hadoop的es系统



