create table secretCustomerSessions (
    id bigserial primary key,
    shop_id bigint references shops(id) on delete cascade,
    customer_id bigint references users(id) on delete cascade,
    stage int not null,
    started timestamptz not null,
    expires_at timestamptz not null
);