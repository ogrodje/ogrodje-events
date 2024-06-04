create table if not exists events
(
    id          char(255) not null
        constraint id primary key,
    meetup_id   char(25),

    kind        char(255) not null,

    name        char(500),
    url         text,
    datetime_at TIMESTAMP,

    created_at  TIMESTAMP default CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP default CURRENT_TIMESTAMP,

    FOREIGN KEY(meetup_id) REFERENCES meetups(id)
);
