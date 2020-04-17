create table feedback(
    id bigserial primary key,
    shop_id bigint not null references shops(id) on delete cascade,
    customer_id bigint not null references users(id) on delete cascade,
    type varchar not null,
    pros varchar not null,
    cons varchar not null,
    additional_info varchar,
    date timestamptz not null
);