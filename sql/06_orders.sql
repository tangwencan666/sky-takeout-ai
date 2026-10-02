USE sky_takeout_ai;
CREATE TABLE IF NOT EXISTS orders (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, number VARCHAR(40) NOT NULL UNIQUE, user_id BIGINT NOT NULL,
 request_key VARCHAR(64) NOT NULL, status VARCHAR(24) NOT NULL, total_amount DECIMAL(12,2) NOT NULL,
 address VARCHAR(300) NOT NULL, contact VARCHAR(100) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_order_request(user_id,request_key), INDEX idx_order_user_time(user_id,created_at),
 CONSTRAINT fk_order_user FOREIGN KEY(user_id) REFERENCES `user`(id),
 CONSTRAINT ck_order_status CHECK(status IN ('PENDING_PAYMENT','WAITING_ACCEPT','PREPARING','DELIVERING','COMPLETED','CANCELLED'))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS order_detail (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, order_id BIGINT NOT NULL, item_type VARCHAR(10) NOT NULL,
 item_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL, unit_price DECIMAL(10,2) NOT NULL,
 quantity INT NOT NULL, flavor VARCHAR(200) NOT NULL,
 CONSTRAINT fk_detail_order FOREIGN KEY(order_id) REFERENCES orders(id), CONSTRAINT ck_detail_quantity CHECK(quantity>0)
) ENGINE=InnoDB;
