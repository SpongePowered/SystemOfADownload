set search_path to version;

create trigger updated_artifact_trigger
    after insert or update of version
    on version.artifact_versions
    for each row
execute procedure version.refreshVersionedTags();

set search_path to public;
