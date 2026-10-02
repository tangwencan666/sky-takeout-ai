USE sky_takeout_ai;
CREATE TABLE IF NOT EXISTS setmeal (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, category_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL,
 price DECIMAL(10,2) NOT NULL, description VARCHAR(500) NOT NULL DEFAULT '', status TINYINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_setmeal_category FOREIGN KEY(category_id) REFERENCES category(id),
 CONSTRAINT ck_setmeal_price CHECK(price>0), CONSTRAINT ck_setmeal_status CHECK(status IN (0,1))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS setmeal_dish (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, setmeal_id BIGINT NOT NULL, dish_id BIGINT NOT NULL, quantity INT NOT NULL,
 UNIQUE KEY uk_setmeal_dish(setmeal_id,dish_id), CONSTRAINT ck_setmeal_quantity CHECK(quantity>0),
 CONSTRAINT fk_sd_setmeal FOREIGN KEY(setmeal_id) REFERENCES setmeal(id),
 CONSTRAINT fk_sd_dish FOREIGN KEY(dish_id) REFERENCES dish(id)
) ENGINE=InnoDB;
