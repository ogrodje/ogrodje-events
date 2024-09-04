alter table events add contact_email varchar(255);
alter table events add featured_at timestamptz;
alter table events add published_at timestamptz default now();
alter table events add mod_token uuid;

