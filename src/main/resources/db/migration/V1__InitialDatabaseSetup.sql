create table users (
  id bigserial primary key,
  first_name varchar not null,
  last_name varchar not null,
  email varchar not null,
  hash varchar not null,
  phone varchar,
  balance float(3) not null default 0,
  role varchar not null default 'Customer'
);

create table shops (
    id bigserial primary key,
    name varchar not null,
    owner bigint not null references users(id) on delete cascade,
    balance float(3) not null default 0,
    address varchar not null
);

create table jwt (
  id varchar primary key,
  jwt varchar not null,
  identity bigint not null references users (id) on delete cascade,
  expiry timestamp not null,
  last_touched timestamp
);
