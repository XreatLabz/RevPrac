create table player_profiles (
    player_id text primary key,
    last_known_name text,
    first_seen_at integer not null,
    last_seen_at integer not null
);

create table player_ratings (
    player_id text not null,
    kit_id text not null,
    rating integer not null check (rating > 0),
    wins integer not null check (wins >= 0),
    losses integer not null check (losses >= 0),
    updated_at integer not null,
    primary key (player_id, kit_id)
);
