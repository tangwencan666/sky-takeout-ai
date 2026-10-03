# 本机真实数据库结构快照

数据库：sky_takeout_ai。只读查询，未访问旧库。

## 列结构

| 表 | 列 | 类型 | 可空 | 键 | 默认值 | 附加 |
| --- | --- | --- | --- | --- | --- | --- |
| category | id | bigint | NO | PRI | NULL | auto_increment |
| category | name | varchar(50) | NO |  | NULL |  |
| category | type | tinyint | NO |  | NULL |  |
| category | sort | int | NO |  | 0 |  |
| category | status | tinyint | NO |  | 1 |  |
| dish | id | bigint | NO | PRI | NULL | auto_increment |
| dish | category_id | bigint | NO | MUL | NULL |  |
| dish | name | varchar(100) | NO |  | NULL |  |
| dish | price | decimal(10,2) | NO |  | NULL |  |
| dish | description | varchar(500) | NO |  |  |  |
| dish | image | varchar(500) | NO |  |  |  |
| dish | status | tinyint | NO | MUL | 0 |  |
| dish_flavor | id | bigint | NO | PRI | NULL | auto_increment |
| dish_flavor | dish_id | bigint | NO | MUL | NULL |  |
| dish_flavor | name | varchar(50) | NO |  | NULL |  |
| dish_flavor | options | varchar(500) | NO |  | NULL |  |
| employee | id | bigint | NO | PRI | NULL | auto_increment |
| employee | username | varchar(32) | NO | UNI | NULL |  |
| employee | password_hash | varchar(100) | NO |  | NULL |  |
| employee | status | tinyint | NO |  | 1 |  |
| employee | created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| order_detail | id | bigint | NO | PRI | NULL | auto_increment |
| order_detail | order_id | bigint | NO | MUL | NULL |  |
| order_detail | item_type | varchar(10) | NO |  | NULL |  |
| order_detail | item_id | bigint | NO |  | NULL |  |
| order_detail | name | varchar(100) | NO |  | NULL |  |
| order_detail | unit_price | decimal(10,2) | NO |  | NULL |  |
| order_detail | quantity | int | NO |  | NULL |  |
| order_detail | flavor | varchar(200) | NO |  | NULL |  |
| orders | id | bigint | NO | PRI | NULL | auto_increment |
| orders | number | varchar(40) | NO | UNI | NULL |  |
| orders | user_id | bigint | NO | MUL | NULL |  |
| orders | request_key | varchar(64) | NO |  | NULL |  |
| orders | status | varchar(24) | NO |  | NULL |  |
| orders | total_amount | decimal(12,2) | NO |  | NULL |  |
| orders | address | varchar(300) | NO |  | NULL |  |
| orders | contact | varchar(100) | NO |  | NULL |  |
| orders | created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| setmeal | id | bigint | NO | PRI | NULL | auto_increment |
| setmeal | category_id | bigint | NO | MUL | NULL |  |
| setmeal | name | varchar(100) | NO |  | NULL |  |
| setmeal | price | decimal(10,2) | NO |  | NULL |  |
| setmeal | description | varchar(500) | NO |  |  |  |
| setmeal | status | tinyint | NO |  | 0 |  |
| setmeal_dish | id | bigint | NO | PRI | NULL | auto_increment |
| setmeal_dish | setmeal_id | bigint | NO | MUL | NULL |  |
| setmeal_dish | dish_id | bigint | NO | MUL | NULL |  |
| setmeal_dish | quantity | int | NO |  | NULL |  |
| shopping_cart | id | bigint | NO | PRI | NULL | auto_increment |
| shopping_cart | user_id | bigint | NO | MUL | NULL |  |
| shopping_cart | item_type | varchar(10) | NO |  | NULL |  |
| shopping_cart | item_id | bigint | NO |  | NULL |  |
| shopping_cart | flavor | varchar(200) | NO |  |  |  |
| shopping_cart | quantity | int | NO |  | NULL |  |
| user | id | bigint | NO | PRI | NULL | auto_increment |
| user | username | varchar(32) | NO | UNI | NULL |  |
| user | password_hash | varchar(100) | NO |  | NULL |  |
| user | status | tinyint | NO |  | 1 |  |
| user | created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |

## 索引

| 表 | 索引 | 非唯一 | 列顺序 | 列 |
| --- | --- | --- | --- | --- |
| category | PRIMARY | 0 | 1 | id |
| dish | fk_dish_category | 1 | 1 | category_id |
| dish | idx_dish_status | 1 | 1 | status |
| dish | PRIMARY | 0 | 1 | id |
| dish_flavor | fk_flavor_dish | 1 | 1 | dish_id |
| dish_flavor | PRIMARY | 0 | 1 | id |
| employee | PRIMARY | 0 | 1 | id |
| employee | username | 0 | 1 | username |
| order_detail | fk_detail_order | 1 | 1 | order_id |
| order_detail | PRIMARY | 0 | 1 | id |
| orders | idx_order_user_time | 1 | 1 | user_id |
| orders | idx_order_user_time | 1 | 2 | created_at |
| orders | number | 0 | 1 | number |
| orders | PRIMARY | 0 | 1 | id |
| orders | uk_order_request | 0 | 1 | user_id |
| orders | uk_order_request | 0 | 2 | request_key |
| setmeal | fk_setmeal_category | 1 | 1 | category_id |
| setmeal | PRIMARY | 0 | 1 | id |
| setmeal_dish | fk_sd_dish | 1 | 1 | dish_id |
| setmeal_dish | PRIMARY | 0 | 1 | id |
| setmeal_dish | uk_setmeal_dish | 0 | 1 | setmeal_id |
| setmeal_dish | uk_setmeal_dish | 0 | 2 | dish_id |
| shopping_cart | PRIMARY | 0 | 1 | id |
| shopping_cart | uk_cart_item | 0 | 1 | user_id |
| shopping_cart | uk_cart_item | 0 | 2 | item_type |
| shopping_cart | uk_cart_item | 0 | 3 | item_id |
| shopping_cart | uk_cart_item | 0 | 4 | flavor |
| user | PRIMARY | 0 | 1 | id |
| user | username | 0 | 1 | username |

## 外键

| 表 | 列 | 引用表 | 引用列 |
| --- | --- | --- | --- |
| dish | category_id | category | id |
| dish_flavor | dish_id | dish | id |
| order_detail | order_id | orders | id |
| orders | user_id | user | id |
| setmeal | category_id | category | id |
| setmeal_dish | dish_id | dish | id |
| setmeal_dish | setmeal_id | setmeal | id |
| shopping_cart | user_id | user | id |
