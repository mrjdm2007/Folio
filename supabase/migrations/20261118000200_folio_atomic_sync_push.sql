-- Atomic optimistic-concurrency write endpoint for Folio clients.
-- Uses SECURITY INVOKER so RLS and the caller's authenticated identity remain in force.

create or replace function public.folio_push_note(
    p_note_id uuid,
    p_expected_revision bigint,
    p_ciphertext text,
    p_nonce text,
    p_key_version integer,
    p_content_hash text,
    p_is_deleted boolean default false
)
returns table (outcome text, current_revision bigint, server_updated_at timestamptz)
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
    v_user_id uuid := auth.uid();
    v_current_revision bigint;
    v_next_revision bigint;
    v_now timestamptz := clock_timestamp();
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;
    if p_expected_revision < 0 or p_key_version < 1 then
        raise exception 'Invalid revision or key version' using errcode = '22023';
    end if;
    if length(p_ciphertext) = 0 or length(p_nonce) = 0 or length(p_content_hash) = 0 then
        raise exception 'Encrypted payload, nonce and hash are required' using errcode = '22023';
    end if;

    select n.revision into v_current_revision
      from public.folio_notes n
     where n.user_id = v_user_id and n.id = p_note_id
     for update;

    if not found then
        if p_expected_revision <> 0 then
            return query select 'conflict'::text, 0::bigint, null::timestamptz;
            return;
        end if;
        v_next_revision := 1;
        insert into public.folio_notes (
            id, user_id, ciphertext, nonce, key_version, revision,
            parent_revision, content_hash, is_deleted, updated_at
        ) values (
            p_note_id, v_user_id, p_ciphertext, p_nonce, p_key_version,
            v_next_revision, p_expected_revision, p_content_hash,
            p_is_deleted, v_now
        );
    else
        if v_current_revision <> p_expected_revision then
            return query
                select 'conflict'::text, v_current_revision, n.updated_at
                  from public.folio_notes n
                 where n.user_id = v_user_id and n.id = p_note_id;
            return;
        end if;
        v_next_revision := v_current_revision + 1;
        update public.folio_notes
           set ciphertext = p_ciphertext,
               nonce = p_nonce,
               key_version = p_key_version,
               revision = v_next_revision,
               parent_revision = p_expected_revision,
               content_hash = p_content_hash,
               is_deleted = p_is_deleted,
               updated_at = v_now
         where user_id = v_user_id and id = p_note_id;
    end if;

    insert into public.folio_note_revisions (
        user_id, note_id, revision, parent_revision, ciphertext,
        nonce, key_version, content_hash, is_deleted, created_at
    ) values (
        v_user_id, p_note_id, v_next_revision, p_expected_revision,
        p_ciphertext, p_nonce, p_key_version, p_content_hash,
        p_is_deleted, v_now
    );

    return query select 'saved'::text, v_next_revision, v_now;
end;
$$;

revoke all on function public.folio_push_note(uuid, bigint, text, text, integer, text, boolean) from public, anon;
grant execute on function public.folio_push_note(uuid, bigint, text, text, integer, text, boolean) to authenticated;
