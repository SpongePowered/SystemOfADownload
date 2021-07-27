set search_path to version;
create or replace function refreshVersionedTags() returns trigger as
'
begin
    refresh materialized view concurrently versioned_tags;
    return null;
end;
' language plpgsql;

set search_path to public;
