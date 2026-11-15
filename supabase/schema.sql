-- ScrollTrack anonymous stats — Supabase schema (free tier).
-- Run this once in the Supabase SQL editor for the scrolltrack-stats project.
--
-- One row per install per day. The app sends only: install_id (random
-- UUID), day, swipes, minutes, app_version. No names, no session times,
-- no content.
--
-- Access model: anon has NO direct table access at all (RLS is on with
-- no policies, and all table privileges are revoked). Anon can only
-- EXECUTE two SECURITY DEFINER functions: submit_stats (writes one row)
-- and stats_summary (reads aggregates only, never individual rows).
-- The table endpoint is deliberately not used: PostgREST's
-- merge-duplicates upsert was rejected by RLS even with permissive
-- INSERT + UPDATE policies (cause unresolved), so all writes go
-- through the function instead.

create table if not exists public.daily_stats (
  install_id text not null,
  day date not null,
  swipes integer not null default 0 check (swipes >= 0 and swipes <= 100000),
  minutes integer not null default 0 check (minutes >= 0 and minutes <= 1440),
  app_version text not null default '',
  updated_at timestamptz not null default now(),
  primary key (install_id, day)
);

-- RLS on, no policies: anon gets nothing directly.
alter table public.daily_stats enable row level security;
drop policy if exists "anon insert" on public.daily_stats;
drop policy if exists "anon upsert" on public.daily_stats;
revoke all on public.daily_stats from anon;

-- Keep updated_at fresh on upsert.
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end; $$;
drop trigger if exists daily_stats_updated_at on public.daily_stats;
create trigger daily_stats_updated_at
  before update on public.daily_stats
  for each row execute function public.set_updated_at();

-- Upload path: validates, then inserts or updates that one row.
-- SECURITY DEFINER so it runs as the table owner and bypasses RLS.
create or replace function public.submit_stats(
  p_install_id text, p_day date, p_swipes integer, p_minutes integer, p_app_version text)
returns void
language plpgsql
security definer
as $$
begin
  if p_install_id is null or p_install_id = '' then raise exception 'install_id required'; end if;
  if p_swipes < 0 or p_swipes > 100000 then raise exception 'swipes out of range'; end if;
  if p_minutes < 0 or p_minutes > 1440 then raise exception 'minutes out of range'; end if;
  if p_day > current_date then raise exception 'day in future'; end if;
  if p_day < current_date - interval '365 days' then raise exception 'day too far in the past'; end if;
  insert into public.daily_stats (install_id, day, swipes, minutes, app_version)
  values (p_install_id, p_day, p_swipes, p_minutes, p_app_version)
  on conflict (install_id, day) do update
    set swipes = excluded.swipes, minutes = excluded.minutes,
        app_version = excluded.app_version, updated_at = now();
end;
$$;
grant execute on function public.submit_stats(text, date, integer, integer, text) to anon;

-- Read path: aggregates only, never individual rows.
create or replace function public.stats_summary()
returns jsonb
language sql
security definer
as $$
  select jsonb_build_object(
    'total_installs', (select count(distinct install_id) from public.daily_stats),
    'total_swipes', (select coalesce(sum(swipes), 0) from public.daily_stats),
    'total_minutes', (select coalesce(sum(minutes), 0) from public.daily_stats),
    'today_swipes', (select coalesce(sum(swipes), 0) from public.daily_stats where day = current_date),
    'today_installs', (select count(distinct install_id) from public.daily_stats where day = current_date),
    'days', (select coalesce(jsonb_agg(jsonb_build_object('day', day, 'swipes', s, 'installs', i) order by day), '[]'::jsonb)
            from (select day, sum(swipes) as s, count(distinct install_id) as i
                  from public.daily_stats group by day) d)
  );
$$;

grant execute on function public.stats_summary() to anon;
