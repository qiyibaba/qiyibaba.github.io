---
layout:     post                    # 使用的布局（不需要改）
title:      insert..on duplicate key update与insert ignore性能比较测试
subtitle:   
date:       2021-04-20              # 时间
author:     Qiyibaba                # 作者
header-img: img/post-bg-202002.jpeg    #这篇文章标题背景图片
catalog: true                       # 是否归档
tags:                               #标签
    - duplicate insert
    - insert ignore
---

## Hey
测试脚本：

date;echo {1..500} | xargs -n 1 -P 50 /bin/sh -c "mysql -uroot -proot -e 'xxxxxxxxx' 1>/dev/null 2>/dev/null";date



测试结果：

[ningyue.lt@h07g16214.sqa.eu95 /home/ningyue.lt]

$date;echo {1..50000} | xargs -n 1 -P 50 /bin/sh -c "mysql -h11.166.78.136 -P32601 -uroot@mysql -e "insert into test.it values ('test_dup') on duplicate key update name=values(name)"";date

2021年 03月 31日 星期三 12:35:41 CST

2021年 03月 31日 星期三 12:36:21 CST

 

[ningyue.lt@h07g16214.sqa.eu95 /home/ningyue.lt]

$date;echo {1..50000} | xargs -n 1 -P 50 /bin/sh -c "mysql -h11.166.78.136 -P32601 -uroot@mysql -e "insert ignore into test.it values ('test_ig')"";date

2021年 03月 31日 星期三 12:36:47 CST

2021年 03月 31日 星期三 12:37:20 CST

 

经过测试后发现，使用insert..ignore性能更好
