create table match_history (
    match_id text primary key,
    player_one_id text not null,
    player_two_id text not null,
    arena_id text not null,
    kit_id text not null,
    match_origin text not null check (match_origin in ('DIRECT_DUEL', 'QUEUE_UNRANKED', 'QUEUE_RANKED')),
    end_reason text not null check (end_reason in ('WIN', 'FORFEIT', 'TIMEOUT', 'SHUTDOWN')),
    winner_id text,
    loser_id text,
    active_ticks integer not null check (active_ticks >= 0),
    completed_at bigint not null,
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

create index match_history_player_one_completed_idx on match_history (player_one_id, completed_at);
create index match_history_player_two_completed_idx on match_history (player_two_id, completed_at);
create index match_history_kit_completed_idx on match_history (kit_id, completed_at);

create table player_kit_stats (
    player_id text not null,
    kit_id text not null,
    matches_played bigint not null check (matches_played >= 0),
    wins bigint not null check (wins >= 0),
    losses bigint not null check (losses >= 0),
    forfeits bigint not null check (forfeits >= 0),
    timeouts bigint not null check (timeouts >= 0),
    shutdowns bigint not null check (shutdowns >= 0),
    updated_at bigint not null,
    primary key (player_id, kit_id)
);
