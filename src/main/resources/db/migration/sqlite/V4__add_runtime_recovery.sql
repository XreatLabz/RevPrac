create table runtime_player_sessions (
    player_id text primary key,
    context text not null check (context in ('QUEUE', 'MATCH', 'SPECTATOR', 'EDITOR')),
    snapshot text not null,
    updated_at integer not null
);

create table runtime_pending_restorations (
    player_id text primary key,
    reason text not null check (reason in ('JOIN', 'QUEUE_JOIN', 'MATCH_START', 'SPECTATE', 'EDITOR_OPEN', 'RETURN_TO_LOBBY', 'QUIT', 'PLUGIN_DISABLE')),
    snapshot text not null,
    updated_at integer not null
);

create table runtime_queue_tickets (
    ticket_id text primary key,
    player_id text not null,
    queue_mode text not null check (queue_mode in ('UNRANKED', 'RANKED')),
    kit_id text not null,
    joined_at_tick integer not null check (joined_at_tick >= 0),
    joined_at_epoch_millis integer not null,
    search_rating integer not null check (search_rating >= 0),
    state text not null check (state in ('SEARCHING', 'PAIRING', 'MATCHED', 'CANCELLED', 'EXPIRED')),
    unique (player_id)
);

create table runtime_matches (
    match_id text primary key,
    player_one_id text not null,
    player_two_id text not null,
    arena_id text not null,
    kit_id text not null,
    match_origin text not null check (match_origin in ('DIRECT_DUEL', 'QUEUE_UNRANKED', 'QUEUE_RANKED')),
    arena_reservation_id text not null,
    countdown_ticks integer not null check (countdown_ticks > 0),
    max_duration_ticks integer not null check (max_duration_ticks > 0),
    spectators_enabled integer not null check (spectators_enabled in (0, 1)),
    match_state text not null check (match_state in ('COUNTDOWN', 'ACTIVE', 'COMPLETED')),
    countdown_ticks_remaining integer not null check (countdown_ticks_remaining >= 0),
    active_ticks_elapsed integer not null check (active_ticks_elapsed >= 0),
    outcome_reason text check (outcome_reason in ('WIN', 'FORFEIT', 'TIMEOUT', 'SHUTDOWN')),
    winner_id text,
    loser_id text,
    completed_at integer,
    updated_at integer not null
);
