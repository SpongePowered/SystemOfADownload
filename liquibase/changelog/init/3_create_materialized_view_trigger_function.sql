set search_path to version;
create or replace function refreshVersionedTags() returns int as
'
    begin
        refresh materialized view version.versioned_tags;
        return 1;
    end;
' language plpgsql;

set search_path to public;
