-- Folio encrypted sync metadata. Apply with Supabase CLI or SQL editor.
create extension if not exists pgcrypto;

create table if not exists public.folio_documents (
  id uuid primary key,
  owner_id uuid not null references auth.users(id) on delete cascade,
  parent_id uuid null,
  folder_id uuid null,
  schema_version integer not null default 1,
  revision bigint not null default 1,
  parent_revision bigint null,
  title_ciphertext bytea not null,
  body_ciphertext bytea not null,
  nonce bytea not null,
  is_pinned boolean not null default false,
  is_archived boolean not null default false,
  is_vault boolean not null default false,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz null
);

create index if not exists folio_documents_owner_updated_idx
  on public.folio_documents(owner_id, updated_at desc);
create index if not exists folio_documents_owner_revision_idx
  on public.folio_documents(owner_id, revision);

create table if not exists public.folio_attachments (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  document_id uuid not null references public.folio_documents(id) on delete cascade,
  object_path text not null,
  content_hash text not null,
  byte_length bigint not null,
  mime_type text not null,
  is_vault boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.folio_device_cursors (
  owner_id uuid not null references auth.users(id) on delete cascade,
  device_id text not null,
  last_seen_revision bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key(owner_id, device_id)
);

alter table public.folio_documents enable row level security;
alter table public.folio_attachments enable row level security;
alter table public.folio_device_cursors enable row level security;

create policy "Users manage their own encrypted documents"
  on public.folio_documents for all
  using (auth.uid() = owner_id)
  with check (auth.uid() = owner_id);

create policy "Users manage their own attachment metadata"
  on public.folio_attachments for all
  using (auth.uid() = owner_id)
  with check (auth.uid() = owner_id);

create policy "Users manage their own device cursors"
  on public.folio_device_cursors for all
  using (auth.uid() = owner_id)
  with check (auth.uid() = owner_id);

-- IMPORTANT: This schema stores encrypted payloads but does not itself encrypt them.
-- Client-side authenticated encryption and private Storage bucket policies are required
-- before production sync is enabled. Do not put plaintext or vault keys in these columns.
