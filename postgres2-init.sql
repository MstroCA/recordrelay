CREATE TABLE customers (
                           id SERIAL PRIMARY KEY,
                           email VARCHAR(255) UNIQUE,
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
                        id SERIAL PRIMARY KEY,
                        customer_id INTEGER REFERENCES customers(id),
                        total_amount NUMERIC(10,2),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
                             id SERIAL PRIMARY KEY,
                             order_id INTEGER REFERENCES orders(id),
                             product_name VARCHAR(255),
                             quantity INTEGER,
                             price NUMERIC(10,2)
);

CREATE TABLE departments (
                             id SERIAL PRIMARY KEY,
                             name VARCHAR(100) NOT NULL
);

INSERT INTO customers(email)
VALUES
    ('user1@test.com'),
    ('user2@test.com');

INSERT INTO orders(customer_id,total_amount)
VALUES
    (1,120.50),
    (2,450.00);

INSERT INTO order_items(order_id,product_name,quantity,price)
VALUES
    (1,'Keyboard',1,120.50),
    (2,'Monitor',2,225.00);
