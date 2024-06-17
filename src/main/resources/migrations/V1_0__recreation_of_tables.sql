create table meetups
(
    id           varchar(25)  not null,
    name         varchar(300) not null,
    homepage_url text,
    meetup_url   text,
    discord_url  text,
    linkedin_url text,
    kompot_url   text,
    ical_url     text,
    created_at   timestamptz default now(),
    updated_at   timestamptz default now()
);

create unique index meetups_id_uindex on meetups (id);

alter table meetups
    add constraint meetups_pk
        primary key (id);

create table events
(
    id        varchar(255) not null,
    meetup_id varchar(25)  not null
        constraint events_meetups_id_fk
            references meetups,
    kind      varchar(255) not null,
    name varchar(500) not null,
    url text,
    location text,
    datetime_start_at timestamptz,
    no_start_time boolean default false,
    datetime_end_at timestamptz,
    no_end_time boolean default false,
    created_at   timestamptz default now(),
    updated_at   timestamptz default now()
);

create unique index events_id_uindex
    on events (id);

alter table events
    add constraint events_pk
        primary key (id);

