USE sky_takeout_ai;
CREATE TABLE IF NOT EXISTS shopping_cart (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 item_type VARCHAR(10) NOT NULL, item_id BIGINT NOT NULL,
 flavor VARCHAR(200) COLLATE utf8mb4_bin NOT NULL DEFAULT '', quantity INT NOT NULL,
 UNIQUE KEY uk_cart_item(user_id,item_type,item_id,flavor),
 CONSTRAINT fk_cart_user FOREIGN KEY(user_id) REFERENCES `user`(id),
 CONSTRAINT ck_cart_type CHECK(item_type IN ('DISH','SETMEAL')), CONSTRAINT ck_cart_quantity CHECK(quantity BETWEEN 1 AND 99)
) ENGINE=InnoDB;
