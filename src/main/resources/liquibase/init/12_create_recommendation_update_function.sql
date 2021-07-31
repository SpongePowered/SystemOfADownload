set search_path to version;
create or replace function refreshVersionRecommendations(in requested_artifact_id varchar(255),
                                                         in requested_group_id varchar(255)) returns int as
'
    declare
        affected int;
    begin
        set search_path to version;

        update artifact_versions v
        set recommended = v.version ~ desired_recommendation.recommendation_regex
        from (select ar.artifact_id,
                     ar.recommendation_regex
              from artifact_recommendations ar
                       join artifacts a on a.id = ar.artifact_id
              where a.group_id = requested_group_id
                and a.artifact_id = requested_artifact_id
             ) as desired_recommendation
        where v.artifact_id = desired_recommendation.artifact_id;
        get diagnostics affected = ROW_COUNT;
        reset search_path;
        return affected;
    end;
' language plpgsql;
reset search_path;
