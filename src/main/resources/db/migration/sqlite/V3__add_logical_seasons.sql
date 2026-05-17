create table seasons (
    season_id text primary key,
    active integer not null check (active in (0, 1)),
    created_at integer not null,
    activated_at integer,
    check (active = 0 or activated_at is not null)
);

create unique index seasons_one_active_idx on seasons (active) where active = 1;

insert into seasons (season_id, active, created_at, activated_at)
values ('default', 1, 0, 0);

alter table match_history add column season_id text not null default 'default';

create table match_history_new (
    season_id text not null,
    match_id text not null,
    player_one_id text not null,
    player_two_id text not null,
    arena_id text not null,
    kit_id text not null,
    match_origin text not null check (match_origin in ('DIRECT_DUEL', 'QUEUE_UNRANKED', 'QUEUE_RANKED')),
    end_reason text not null check (end_reason in ('WIN', 'FORFEIT', 'TIMEOUT', 'SHUTDOWN')),
    winner_id text,
    loser_id text,
    active_ticks integer not null check (active_ticks >= 0),
    completed_at integer not null,
    primary key (season_id, match_id),
    check (player_one_id <> player_two_id),
    check (
        (
            end_reason in ('WIN', 'FORFEIT')
            and winner_id is not null
            and loser_id is not null
            and winner_id <> loser_id
            and (winner_id = player_one_id or winner_id = player_two_id)
            and (loser_id = player_one_id or loser_id = player_two_id)
        )
        or (
            end_reason in ('TIMEOUT', 'SHUTDOWN')
            and winner_id is null
            and loser_id is null
        )
    )
);

insert into match_history_new (
    season_id,
    match_id,
    player_one_id,
    player_two_id,
    arena_id,
    kit_id,
    match_origin,
    end_reason,
    winner_id,
    loser_id,
    active_ticks,
    completed_at
)
select
    season_id,
    match_id,
    player_one_id,
    player_two_id,
    arena_id,
    kit_id,
    match_origin,
    end_reason,
    winner_id,
    loser_id,
    active_ticks,
    completed_at
from match_history;

drop table match_history;
alter table match_history_new rename to match_history;

create index match_history_season_player_one_completed_idx
    on match_history (season_id, player_one_id, completed_at);
create index match_history_season_player_two_completed_idx
    on match_history (season_id, player_two_id, completed_at);
create index match_history_season_kit_completed_idx
    on match_history (season_id, kit_id, completed_at);

create table player_ratings_new (
    season_id text not null,
    player_id text not null,
    kit_id text not null,
    rating integer not null check (rating > 0),
    wins integer not null check (wins >= 0),
    losses integer not null check (losses >= 0),
    updated_at integer not null,
    primary key (season_id, player_id, kit_id)
);

insert into player_ratings_new (season_id, player_id, kit_id, rating, wins, losses, updated_at)
select 'default', player_id, kit_id, rating, wins, losses, updated_at from player_ratings;

drop table player_ratings;
alter table player_ratings_new rename to player_ratings;

create table player_kit_stats_new (
    season_id text not null,
    player_id text not null,
    kit_id text not null,
    matches_played integer not null check (matches_played >= 0),
    wins integer not null check (wins >= 0),
    losses integer not null check (losses >= 0),
    forfeits integer not null check (forfeits >= 0),
    timeouts integer not null check (timeouts >= 0),
    shutdowns integer not null check (shutdowns >= 0),
    updated_at integer not null,
    primary key (season_id, player_id, kit_id)
);

insert into player_kit_stats_new (
    season_id,
    player_id,
    kit_id,
    matches_played,
    wins,
    losses,
    forfeits,
    timeouts,
    shutdowns,
    updated_at
)
select
    'default',
    player_id,
    kit_id,
    matches_played,
    wins,
    losses,
    forfeits,
    timeouts,
    shutdowns,
    updated_at
from player_kit_stats;

drop table player_kit_stats;
alter table player_kit_stats_new rename to player_kit_stats;
