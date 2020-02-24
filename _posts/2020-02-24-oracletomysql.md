---
layout:     post                    
title:     Oracle To MySQL语法改造
subtitle:   本文是从oracle官方文档中提取的语法寻找对应的MySQL语法
date:       2020-02-24            
author:     Qiyibaba               
header-img: img/post-bg-202002.jpg   
catalog: true                     
tags:                               
    - oracle
    - mysql
---

### 

#### PREDICTION_PROBABILITY

```sql
SELECT cust_id FROM (
   SELECT cust_id
   FROM mining_data_apply_v
   WHERE country_name = 'Italy'
   ORDER BY PREDICTION_PROBABILITY(DT_SH_Clas_sample, 1 USING *)
      DESC, cust_id)
   WHERE rownum < 11
```

#### PREDICTION_SET

```mysql
SELECT T.cust_id, S.prediction, S.probability, S.cost
  FROM (SELECT cust_id,
PREDICTION_SET(dt_sh_clas_sample COST MODEL USING *) pset
          FROM mining_data_apply_v
         WHERE cust_id < 100011) T,
       TABLE(T.pset) S
ORDER BY cust_id, S.prediction
```

#### SYS_CONTEXT

1.部分可以使用MySQL系统函数替代

```
eg：
O：select SYS_CONTEXT ('USERENV', 'DB_NAME') db_name;
M：select database();
```

2.部分可以使用系统变量替代

```
eg：
O：select SYS_CONTEXT ('USERENV', 'OS_USER') os_user from dual
M：show variables like 'hostname';
```

3.可使用自定义函数将对应关系进行整理输出

#### SYS_GUID和RAW类型

​	RAW，类似于CHAR，声明方式RAW(L)，L为长度，以字节为单位，作为数据库列最大2000，作为变量最大32767字节。 LONG RAW，类似于LONG，作为数据库列最大存储2G字节的数据，作为变量最大32760字节。mysql的blob类型最大支持4K，能够兼容raw类型的2k

```sql
SQL> create table locations(uid_col RAW(16));
Table created

SQL> insert into locations values (SYS_GUID());
1 row inserted

SQL> select * from locations;
UID_COL
--------------------------------
FD71C905738C434D93860909603F6FDB
```

```mysql
mysql> create table locations(uid_col blob);
Query OK, 0 rows affected (0.13 sec)

mysql> insert into locations values (UUID());
Query OK, 1 row affected (0.00 sec)

mysql> select * from locations;
+--------------------------------------+
| uid_col                              |
+--------------------------------------+
| ee1133ec-56ac-11ea-9ea6-744aa4020a3d |
+--------------------------------------+
1 row in set (0.00 sec)

mysql> insert into locations values (replace(uuid(),'-',''));
Query OK, 1 row affected (0.01 sec)

mysql> select * from locations;
+--------------------------------------+
| uid_col                              |
+--------------------------------------+
| ee1133ec-56ac-11ea-9ea6-744aa4020a3d |
| 4698c91756ad11ea9ea6744aa4020a3d     |
+--------------------------------------+
2 rows in set (0.00 sec)
```

#### SYS_TYPEID

​	该函数用于返回唯一的类型ID值。只能在对象类型操作数上使用此函数,即使用“CREATE TYPE .. as OBJECT”模式创建的类型。MySQL不支持。

```mysql
CREATE TYPE employee_t UNDER person_t 
   (department_id NUMBER, salary NUMBER) NOT FINAL;
/

SQL> CREATE TYPE person_t AS OBJECT (name VARCHAR2(100), ssn NUMBER)
  2     NOT FINAL;
  3  /
Type created

SQL> CREATE TABLE books (title VARCHAR2(100), author person_t);
Table created

SQL> insert into books values ('An Autobiography',person_t('Bob',10));
1 row inserted

SQL> insert into books values ('Business Rules',person_t('Joe',20));
1 row inserted

SQL> insert into books values ('Mixing School and Work',person_t('Tim',30));
1 row inserted

SQL> select * from books;
TITLE                        AUTHOR
---------------------------- ------
An Autobiography             <Objec
Business Rules               <Objec
Mixing School and Work       <Objec

SQL> SELECT b.title, b.author.name, SYS_TYPEID(author) "Type_ID" FROM books b;
TITLE                         AUTHOR.NAME     Type_ID
----------------------------- --------------- -------------
An Autobiography              Bob             01
Business Rules                Joe             01
Mixing School and Work        Tim             01

SQL> 
```

#### COLLECT

用于根据输入列和被选中行建立嵌套表结果。该函数同样也使用在自定义类型上，也不支持。

```sql
SELECT CAST(COLLECT(phone_numbers) AS phone_book_t) 'Income Level L Phone Book'
  FROM customers
  WHERE income_level = 'L: 300,000 and above'

SELECT CAST(COLLECT(warehouse_name ORDER BY warehouse_name)
       AS warehouse_name_t) Warehouses
   FROM warehouses
   
SQL> create or replace type varchar2_app as table of varchar2(2000);
  2  /
Type created

SQL> select cast(collect(title order by title) as varchar2_app) from books;
CAST(COLLECT(TITLEORDERBYTITLE
------------------------------
<Object>
```

#### IS [NOT] NAN Example

```
SELECT COUNT(*) FROM employees  WHERE commission_pct IS NOT NAN
-- IS NAN：匹配NAN这个特殊值，“非数字” 
-- IS NOT NAN：与上面意思相反
```

MySQL没有NaN。对于可能使用NaN的地方，例如SQRT（-1），MySQL通常返回NULL

```mysql
mysql> select sqrt(-1);
+----------+
| sqrt(-1) |
+----------+
|     NULL |
+----------+
1 row in set (0.00 sec)
-- MySQL至少在一种方式上是相似的：NULL不等于任何数值，也不等于NULL
mysql> select null = null;
+-------------+
| null = null |
+-------------+
|        NULL |
+-------------+
1 row in set (0.00 sec)
```

#### IS [NOT] INFINITE Example

```
SELECT last_name FROM employees   WHERE salary IS NOT INFINITE
-- IS INFINITE：匹配BINARY_FLOAT和BINARY_DOUBLE中的“无穷”值
-- IS NOT INFINITE：与上面意思相反
```

#### IS OF TYPE和IS OF

```
SELECT * FROM persons p     WHERE VALUE(p) IS OF TYPE (employee_t)
SELECT * FROM persons p     WHERE VALUE(p) IS OF (ONLY part_time_emp_t)
```

自定义类型使用,MySQL不支持

#### Using PIVOT and UNPIVOT

PIVOT 和UNPIVOT是用来行列互转的，下例展示如何使用，语法说明：

```sql
select * from table_name pivot(max(column_name)  --行转列后的列的值value，聚合函数是必须要有的
 for column_name in(value_1,value_2,value_3) --需要行转列的列及其对应列的属性1/2/3
```

oracle实例：

```sql
create table STU_ROW2COL(id VARCHAR2(10),intname VARCHAR2(10),subject VARCHAR2(20),grade   NUMBER);

insert into stu_row2col (ID, INTNAME, SUBJECT, GRADE) values ('1', 'ZORRO', '语文', 70);
insert into stu_row2col (ID, INTNAME, SUBJECT, GRADE) values ('2', 'ZORRO', '数学', 80);
insert into stu_row2col (ID, INTNAME, SUBJECT, GRADE) values ('3', 'ZORRO', '英语', 75);
insert into stu_row2col (ID, INTNAME, SUBJECT, GRADE) values ('4', 'SEKER', '语文', 65);
insert into stu_row2col (ID, INTNAME, SUBJECT, GRADE) values ('5', 'SEKER', '数学', 75);
insert into stu_row2col (ID, INTNAME, SUBJECT, GRADE) values ('6', 'SEKER', '英语', 60);
insert into stu_row2col (ID, INTNAME, SUBJECT, GRADE) values ('7', 'BLUES', '语文', 60);
insert into stu_row2col (ID, INTNAME, SUBJECT, GRADE) values ('8', 'BLUES', '数学', 90);
insert into stu_row2col (ID, INTNAME, SUBJECT, GRADE) values ('9', 'PG', '数学', 80);
insert into stu_row2col (ID, INTNAME, SUBJECT, GRADE) values ('10', 'PG', '英语', 90);

SQL> select t.* from stu_row2col t ;
ID         INTNAME    SUBJECT                   GRADE
---------- ---------- -------------------- ----------
1          ZORRO      语文                         70
2          ZORRO      数学                         80
3          ZORRO      英语                         75
4          SEKER      语文                         65
5          SEKER      数学                         75
6          SEKER      英语                         60
7          BLUES      语文                         60
8          BLUES      数学                         90
9          PG         数学                         80
10         PG         英语                         90
10 rows selected
--使用pivot
SQL> select * from ( select t.intname,t.subject,t.grade from stu_row2col t) pivot(sum(grade) for subject in ('语文' 语文,'数学' 数学,'英语' 英语));
INTNAME            语文         数学         英语
---------- ---------- ---------- ----------
SEKER              65         75         60
BLUES              60         90 
PG                            80         90
ZORRO              70         80         75
--使用decode函数
SQL> select intname,max(decode(subject, '语文', grade)) 语文,max(decode(subject, '数学', grade)) 数学,max(decode(subject, '英语', grade)) 英语 from stu_row2col group by intname;
INTNAME            语文         数学         英语
---------- ---------- ---------- ----------
SEKER              65         75         60
BLUES              60         90 
PG                            80         90
ZORRO              70         80         75
--使用case when
SQL> select
  2     intname,
  3     max(case when subject = '语文' then grade end) 语文,
  4     max(case when subject = '数学' then grade end) 数学,
  5     max(case when subject = '英语' then grade end) 英语
  6   from stu_row2col
  7     group by intname;
INTNAME            语文         数学         英语
---------- ---------- ---------- ----------
SEKER              65         75         60
BLUES              60         90 
PG                            80         90
ZORRO              70         80         75
--列转行
SQL> create or replace view stu_col2row as
  2  select "INTNAME","语文","数学","英语" from ( select t.intname,t.subject,t.grade from stu_row2col t) pivot(sum(grade) for subject in ('语文' 语文,'数学' 数学,'英语' 英语));
View created

SQL> select INTNAME 姓名,km 科目, fs 分数 from stu_col2row unpivot(fs for km in(语文,数学,英语));
姓名       科目           分数
---------- ------ ----------
SEKER      语文           65
SEKER      数学           75
SEKER      英语           60
BLUES      语文           60
BLUES      数学           90
PG         数学           80
PG         英语           90
ZORRO      语文           70
ZORRO      数学           80
ZORRO      英语           75
10 rows selected
```

对MySQL而言，可以使用case when替换：

```
mysql> select    intname,    max(case when subject = '语文' then grade end) 语文,    max(case when subject = '数学' then grade end) 数学,    max(case when subject = '英语' then grade end) 英语  from stu_row2col    group by intname;
+---------+--------+--------+--------+
| intname | 语文   | 数学   | 英语   |
+---------+--------+--------+--------+
| BLUES   |     60 |     90 |   NULL |
| PG      |   NULL |     80 |     90 |
| SEKER   |     65 |     75 |     60 |
| ZORRO   |     70 |     80 |     75 |
+---------+--------+--------+--------+
4 rows in set (0.00 sec)
```

