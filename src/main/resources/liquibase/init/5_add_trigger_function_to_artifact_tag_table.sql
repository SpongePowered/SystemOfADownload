set search_path to version;

create trigger updated_tag_trigger
    after insert or update or delete
    on version.artifact_tags
    for each row
execute procedure version.refreshVersionedTags();

set search_path to public;
