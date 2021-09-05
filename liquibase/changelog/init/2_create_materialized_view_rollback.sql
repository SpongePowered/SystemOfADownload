set search_path to version;

drop materialized view if exists version.versioned_tags cascade;

set search_path to public;
