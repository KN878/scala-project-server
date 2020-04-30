create table actions(
    id bigserial primary key,
    shop_id bigint not null references shops(id) on delete cascade,
    action varchar not null
);