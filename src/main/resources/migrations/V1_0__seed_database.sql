create table if not exists meetups
(
    id          char(25)  not null
        constraint id primary key,
    name        char(300) not null,
    homePageUrl text,
    meetupUrl   text,
    discordUrl  text,
    linkedInUrl text,
    created_at  TIMESTAMP default CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP default CURRENT_TIMESTAMP
);
