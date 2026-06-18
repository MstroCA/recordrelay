CREATE TABLE departments (
                             id SERIAL PRIMARY KEY,
                             name VARCHAR(100) NOT NULL
);

CREATE TABLE employees (
                           id SERIAL PRIMARY KEY,
                           first_name VARCHAR(100),
                           last_name VARCHAR(100),
                           department_id INTEGER REFERENCES departments(id),
                           salary NUMERIC(12,2)
);

INSERT INTO departments(name)
VALUES
    ('Engineering'),
    ('Sales'),
    ('HR');

INSERT INTO employees(first_name,last_name,department_id,salary)
VALUES
    ('John','Doe',1,85000),
    ('Jane','Smith',1,92000),
    ('Bob','Wilson',2,65000),
    ('Alice','Brown',3,60000);
