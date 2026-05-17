create table seasons (
    season_id text primary key,
    active boolean not null,
    created_at bigint not null,
    activated_at bigint,
    check (not active or activated_at is not null)
);

create unique index seasons_one_active_idx on seasons (active) where active;

insert into seasons (season_id, active, created_at, activated_at)
values ('default', true, 0, 0);

alter table match_history add column season_id text;
update match_history set season_id = 'default' where season_id is null;
alter table match_history alter column season_id set not null;
alter table match_history drop constraint match_history_pkey;
alter table match_history add primary key (season_id, match_id);

create index match_history_season_player_one_completed_idx
    on match_history (season_id, player_one_id, completed_at);
create index match_history_season_player_two_completed_idx
    on match_history (season_id, player_two_id, completed_at);
create index match_history_season_kit_completed_idx
    on match_history (season_id, kit_id, completed_at);

alter table player_ratings add column season_id text;
update player_ratings set season_id = 'default' where season_id is null;
alter table player_ratings alter column season_id set not null;
alter table player_ratings drop constraint player_ratings_pkey;
alter table player_ratings add primary key (season_id, player_id, kit_id);

alter table player_kit_stats add column season_id text;
update player_kit_stats set season_id = 'default' where season_id is null;
alter table player_kit_stats alter column season_id set not null;
alter table player_kit_stats drop constraint player_kit_stats_pkey;
alter table player_kit_stats add primary key (season_id, player_id, kit_id);
