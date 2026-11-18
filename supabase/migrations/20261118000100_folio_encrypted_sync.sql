-- Folio encrypted sync foundation.
-- Apply with Supabase CLI: supabase db push
-- Clients must encrypt payloads before upload. This schema intentionally stores opaque ciphertext only.

create extension if not exists pgcrypto;

-- The earlier core migration created a different legacy folio_attachments shape
-- (owner_id/document_id/object_path). Move it aside before creating the encrypted
-- sync table, whose policies and clients use user_id/note_id/storage_path.
do $
begin
    if to_regclass('public.folio_attachments') is not null
       and exists (
           select 1 from information_schema.columns
            where table_schema = 'public' and table_name = 'folio_attachments'
              and column_name = 'owner_id'
       )
       and not exists (
           select 1 from information_schema.columns
            where table_schema = 'public' and table_name = 'folio_attachments'
              and column_name = 'user_id'
       ) then
        alter table public.folio_attachments rename to folio_document_attachments;
    end if;
end
$;

create table if not exists public.folio_notes (
    id uuid not null,
    user_id uuid not null references auth.users(id) on delete cascade,
    ciphertext text not null,
    nonce text not null,
    key_version integer not null default 1,
    revision bigint not null default 1,
    parent_revision bigint not null default 0,
    content_hash text not null,
    is_deleted boolean not null default false,
    updated_at timestamptz not null default now(),
    primary key (user_id, id),
    constraint folio_notes_revision_positive check (revision > 0),
    constraint folio_notes_parent_nonnegative check (parent_revision >= 0),
    constraint folio_notes_key_version_positive check (key_version > 0)
);

create index if not exists folio_notes_user_updated_idx
    on public.folio_notes (user_id, updated_at desc);
create index if not exists folio_notes_user_revision_idx
    on public.folio_notes (user_id, revision);

create table if not exists public.folio_note_revisions (
    user_id uuid not null references auth.users(id) on delete cascade,
    note_id uuid not null,
    revision bigint not null,
    parent_revision bigint not null,
    ciphertext text not null,
    nonce text not null,
    key_version integer not null,
    content_hash text not null,
    is_deleted boolean not null default false,
    created_at timestamptz not null default now(),
    primary key (user_id, note_id, revision),
    constraint folio_revision_note_fk foreign key (user_id, note_id)
        references public.folio_notes(user_id, id) on delete cascade
);

create index if not exists folio_note_revisions_lookup_idx
    on public.folio_note_revisions (user_id, note_id, revision desc);

create table if not exists public.folio_attachments (
    id uuid not null,
    user_id uuid not null references auth.users(id) on delete cascade,
    note_id uuid not null,
    storage_path text not null,
    ciphertext_hash text not null,
    byte_count bigint not null,
    content_type text not null,
    key_version integer not null default 1,
    created_at timestamptz not null default now(),
    primary key (user_id, id),
    constraint folio_attachment_size_nonnegative check (byte_count >= 0),
    constraint folio_attachment_note_fk foreign key (user_id, note_id)
        references public.folio_notes(user_id, id) on delete cascade
);

alter table public.folio_notes enable row level security;
alter table public.folio_note_revisions enable row level security;
alter table public.folio_attachments enable row level security;

drop policy if exists "Users can read their Folio notes" on public.folio_notes;
create policy "Users can read their Folio notes" on public.folio_notes
    for select to authenticated using (auth.uid() = user_id);
drop policy if exists "Users can insert their Folio notes" on public.folio_notes;
create policy "Users can insert their Folio notes" on public.folio_notes
    for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists "Users can update their Folio notes" on public.folio_notes;
create policy "Users can update their Folio notes" on public.folio_notes
    for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
drop policy if exists "Users can delete their Folio notes" on public.folio_notes;
create policy "Users can delete their Folio notes" on public.folio_notes
    for delete to authenticated using (auth.uid() = user_id);

drop policy if exists "Users can read their Folio revisions" on public.folio_note_revisions;
create policy "Users can read their Folio revisions" on public.folio_note_revisions
    for select to authenticated using (auth.uid() = user_id);
drop policy if exists "Users can insert their Folio revisions" on public.folio_note_revisions;
create policy "Users can insert their Folio revisions" on public.folio_note_revisions
    for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "Users can read their Folio attachments" on public.folio_attachments;
create policy "Users can read their Folio attachments" on public.folio_attachments
    for select to authenticated using (auth.uid() = user_id);
drop policy if exists "Users can insert their Folio attachments" on public.folio_attachments;
create policy "Users can insert their Folio attachments" on public.folio_attachments
    for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists "Users can update their Folio attachments" on public.folio_attachments;
create policy "Users can update their Folio attachments" on public.folio_attachments
    for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
drop policy if exists "Users can delete their Folio attachments" on public.folio_attachments;
create policy "Users can delete their Folio attachments" on public.folio_attachments
    for delete to authenticated using (auth.uid() = user_id);

-- Append-only revision history is enforced for authenticated clients.
revoke update, delete on public.folio_note_revisions from authenticated;
grant select, insert on public.folio_note_revisions to authenticated;
grant select, insert, update, delete on public.folio_notes to authenticated;
grant select, insert, update, delete on public.folio_attachments to authenticated;

-- Private bucket. Object paths must begin with the authenticated user's UUID.
insert into storage.buckets (id, name, public, file_size_limit)
values ('folio-encrypted-attachments', 'folio-encrypted-attachments', false, 52428800)
on conflict (id) do update set public = false, file_size_limit = 52428800;

drop policy if exists "Folio users read own encrypted attachments" on storage.objects;
create policy "Folio users read own encrypted attachments" on storage.objects
    for select to authenticated using (
        bucket_id = 'folio-encrypted-attachments'
        and (storage.foldername(name))[1] = auth.uid()::text
    );
drop policy if exists "Folio users upload own encrypted attachments" on storage.objects;
create policy "Folio users upload own encrypted attachments" on storage.objects
    for insert to authenticated with check (
        bucket_id = 'folio-encrypted-attachments'
        and (storage.foldername(name))[1] = auth.uid()::text
    );
drop policy if exists "Folio users update own encrypted attachments" on storage.objects;
create policy "Folio users update own encrypted attachments" on storage.objects
    for update to authenticated using (
        bucket_id = 'folio-encrypted-attachments'
        and (storage.foldername(name))[1] = auth.uid()::text
    ) with check (
        bucket_id = 'folio-encrypted-attachments'
        and (storage.foldername(name))[1] = auth.uid()::text
    );
drop policy if exists "Folio users delete own encrypted attachments" on storage.objects;
create policy "Folio users delete own encrypted attachments" on storage.objects
    for delete to authenticated using (
        bucket_id = 'folio-encrypted-attachments'
        and (storage.foldername(name))[1] = auth.uid()::text
    );
